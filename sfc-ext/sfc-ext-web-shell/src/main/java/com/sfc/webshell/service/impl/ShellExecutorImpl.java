package com.sfc.webshell.service.impl;

import com.sfc.webshell.model.ShellExecuteParameter;
import com.sfc.webshell.model.ShellExecuteResult;
import com.sfc.webshell.service.ShellExecuteRecordService;
import com.sfc.webshell.service.ShellExecutor;
import com.sfc.webshell.utils.ProcessUtils;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.CommonResult;
import com.xiaotao.saltedfishcloud.model.RequestParam;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

    private Process createProcess(String workDir, String originCmd) throws IOException {
        log.debug("执行命令：{}", originCmd);
        String executablePath = ProcessUtils.resolveCmdExecutablePath(workDir, originCmd);
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
        if (nodeId != null && !clusterService.getSelf().getId().equals(nodeId)) {
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

}
