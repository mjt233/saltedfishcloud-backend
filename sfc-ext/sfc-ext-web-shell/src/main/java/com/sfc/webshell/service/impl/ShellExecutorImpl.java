package com.sfc.webshell.service.impl;

import com.sfc.webshell.model.ShellExecuteParameter;
import com.sfc.webshell.model.ShellExecuteResult;
import com.sfc.webshell.service.ShellExecuteRecordService;
import com.sfc.webshell.service.ShellExecutor;
import com.sfc.webshell.utils.ProcessUtils;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.CommonResult;
import com.xiaotao.saltedfishcloud.model.RequestParam;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
@Slf4j
public class ShellExecutorImpl implements ShellExecutor {
    @Autowired
    private ShellExecuteRecordService shellExecuteRecordService;

    @Autowired
    private ClusterService clusterService;

    private final static List<String> WINDOWS_EXECUTABLE_SUFFIX_LIST = List.of(".exe", ".EXE", ".bat", ".BAT", ".cmd", ".CMD");

    private Process createProcess(String workDir, String originCmd) throws IOException {
        log.debug("执行命令：{}", originCmd);
        String executablePath = resolveCmdExecutablePath(workDir, originCmd);
        List<String> args = ProcessUtils.parseCommandArgs(originCmd);
        args.set(0, executablePath);

        Process process = new ProcessBuilder()
                .command(args)
                .redirectErrorStream(true)
                .directory(new File(workDir))
                .start();
        log.debug("命令{} pid为: {}", originCmd, process.toHandle().pid());
        return process;
    }

    /**
     * 监控进程执行超时
     * @param process   待监控进程
     * @param timeout   等待时间，若小于等于0表示不监控，单位: 秒
     * @param onTimeoutCallback 超时回调
     */
    private void watchTimeout(Process process, long timeout, Consumer<Process> onTimeoutCallback) {
        if (timeout <= 0) {
            return;
        }
        new Thread(() -> {
            long begin = System.currentTimeMillis();
            while (process.isAlive()) {
                try {
                    Thread.sleep(1000);
                    long duration = (System.currentTimeMillis() - begin) / 1000;
                    if (duration >= timeout) {
                        onTimeoutCallback.accept(process);
                        return;
                    }
                } catch (InterruptedException ignore) { }
            }
        }).start();
    }

    @Override
    public ShellExecuteResult executeCommand(Long nodeId, ShellExecuteParameter parameter) throws IOException {
        if (nodeId != null) {
            ClusterNodeInfo node = Optional
                    .ofNullable(clusterService.getNodeById(nodeId))
                    .orElseThrow(() -> new IllegalArgumentException("无效的节点id:" + nodeId));
            RequestParam requestParam = RequestParam.builder()
                    .method(HttpMethod.POST)
                    .url("/api/webShell/executeCommand")
                    .body(parameter)
                    .build();

            ResponseEntity<CommonResult<ShellExecuteResult>> httpCallResult = clusterService.request(
                    node.getId(),
                    requestParam,
                    new ParameterizedTypeReference<>() {}
            );
            return Objects.requireNonNull(httpCallResult.getBody()).getData();
        }

        // 记录执行命令
        String workDir = Paths.get("").toAbsolutePath().toString();
        String cmd = parameter.getCmd();
        shellExecuteRecordService.addCmdRecord(workDir, cmd);

        long begin = System.currentTimeMillis();
        ShellExecuteResult result = new ShellExecuteResult();
        StringBuilder processOutput = new StringBuilder();

        try {
            AtomicBoolean isTimeout = new AtomicBoolean();
            // 创建进程并执行
            Process process = createProcess(workDir, cmd);
            long pid = process.toHandle().pid();
            Charset charset = Optional.ofNullable(parameter.getCharset())
                    .map(Charset::forName)
                    .orElse(StandardCharsets.UTF_8);
            watchTimeout(process, parameter.getTimeout(), p -> {
                isTimeout.set(true);
                p.destroy();
            });

            // 记录输出日志
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
                String line;
                while ((line =reader.readLine()) != null) {
                    processOutput.append(line).append('\n');
                    log.debug("[{}]命令执行中输出: {}", pid, line);
                }
                result.setExitCode(process.waitFor());
            }
            log.info("命令执行完成，输出为: \n{}", processOutput);
            if (isTimeout.get()) {
                processOutput.append("\n[警告]命令执行超时，已被kill\n");
            }
        } catch (Throwable throwable) {
            log.error("命令执行出错", throwable);
            processOutput.append("命令执行出错:").append(throwable.getMessage());
        }

        result.setOutput(processOutput.toString());
        result.setTime(System.currentTimeMillis() - begin);
        return result;
    }

    /**
     * 通过环境变量PATH解析命令行主命令的可执行命令路径
     * @param workDir   工作目录
     * @param cmd       命令
     * @return          命令的可执行路径
     */
    private String resolveCmdExecutablePath(String workDir, String cmd) {
        String[] cmds = cmd.split("\\s+", 2);
        if (Files.isExecutable(Paths.get(cmds[0]))) {
            return cmd;
        }

        Path curPathFile = Paths.get(workDir).resolve(cmds[0]);
        if (Files.exists(curPathFile)) {
            return curPathFile.toAbsolutePath().toString();
        }
        return Arrays.stream(Optional.ofNullable(System.getenv("PATH")).orElse("")
                .split(";"))
                .map(envPath -> {
                    Path path = Paths.get(StringUtils.appendPath(envPath, cmds[0]));
                    if (Files.isExecutable(path)) {
                        return path.toString();
                    }
                    if (OSInfo.isWindows()) {
                        return WINDOWS_EXECUTABLE_SUFFIX_LIST
                                .stream()
                                .map(suffix -> path + suffix)
                                .filter(windowsPath -> Files.isExecutable(Paths.get(windowsPath)))
                                .findAny()
                                .orElse(null);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("找不到命令 " + cmds[0]));

    }
}
