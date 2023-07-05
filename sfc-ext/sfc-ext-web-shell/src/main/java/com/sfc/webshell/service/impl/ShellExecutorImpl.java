package com.sfc.webshell.service.impl;

import com.sfc.rpc.RPCManager;
import com.sfc.rpc.RPCRequest;
import com.sfc.rpc.RPCResponse;
import com.sfc.webshell.constans.WebShellMQTopic;
import com.sfc.webshell.constans.WebShellRpcFunction;
import com.sfc.webshell.helper.BlockStringBuffer;
import com.sfc.webshell.model.ShellExecuteParameter;
import com.sfc.webshell.model.ShellExecuteResult;
import com.sfc.webshell.model.ShellSessionRecord;
import com.sfc.webshell.service.ShellExecuteRecordService;
import com.sfc.webshell.service.ShellExecutor;
import com.sfc.webshell.utils.ProcessUtils;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.CommonResult;
import com.xiaotao.saltedfishcloud.model.RequestParam;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.service.MQService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ShellExecutorImpl implements ShellExecutor, InitializingBean {
    private final static String LOG_PREFIX = "[WebShell]";
    @Autowired
    private ShellExecuteRecordService shellExecuteRecordService;

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private RPCManager rpcManager;

    @Autowired
    private MQService mqService;

    private final Map<Long, Process> processMap = new ConcurrentHashMap<>();

    private final Map<Long, BlockStringBuffer> outputMap = new ConcurrentHashMap<>();

    private final Map<Long, ShellSessionRecord> sessionMap = new ConcurrentHashMap<>();

    private final AtomicInteger threadCnt = new AtomicInteger(0);

    private final Executor forceKillPool = new ThreadPoolExecutor(
            0,
            10,
            10, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10),
            (r) -> new Thread(r, "web-shell-killer")
    );

    private final Executor threadPool = new ThreadPoolExecutor(
            0,
            1024,
            1,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            (r) -> new Thread(r, "web-shell-io-" + threadCnt.getAndIncrement()),
            (r, executor) -> {
                throw new RejectedExecutionException("节点 [" + clusterService.getSelf().getHost() + "] 的WebShell执行线程池已满");
            }
    );

    @Override
    public void afterPropertiesSet() throws Exception {
        // 注册结束进程RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.KILL, request -> {
            Long sessionId = request.getTaskId();
            try {
                killLocal(sessionId, 60000);
                return RPCResponse.success(null);
            } catch (JsonException e) {
                return RPCResponse.ingore();
            }
        });

        // 注册获取会话列表RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.LIST_SESSION, request -> RPCResponse.success(new ArrayList<>(sessionMap.values())));

        // 注册获取日志RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.GET_OUTPUT_LOG, request -> {
            Long sessionId = Long.valueOf(request.getParam());
            String localResult = getLocalOutputLog(sessionId);
            if (localResult == null) {
                return RPCResponse.ingore();
            }
            return RPCResponse.success(localResult);
        });
    }

    /**
     * 创建进程
     * @param parameter 参数
     * @return          进程
     */
    private Process createProcess(ShellExecuteParameter parameter) throws IOException {
        String originCmd = Optional.ofNullable(parameter.getShell()).orElse(parameter.getCmd());
        String workDir = Optional.ofNullable(parameter.getWorkDirectory()).orElseGet(() -> Paths.get("").toAbsolutePath().toString());

        log.debug("{}在{}执行命令：{}", LOG_PREFIX, workDir, originCmd);
        Map<String, String> envMap = parameter.getEnv();
        String executablePath = ProcessUtils.resolveCmdExecutablePath(
                workDir,
                originCmd,
                Optional.ofNullable(envMap).map(e -> e.get("PATH")).orElse(null)
        );
        List<String> args = ProcessUtils.parseCommandArgs(originCmd);
        args.set(0, executablePath);

        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(args)
                .redirectErrorStream(true)
                .directory(new File(workDir));

        if (envMap != null) {
            processBuilder.environment().putAll(envMap);
        }


        Process process = processBuilder.start();
        log.debug("{}命令{} pid为: {}", LOG_PREFIX, originCmd, process.toHandle().pid());
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
        CommonResult<ShellExecuteResult> rpcResult = invokeNodeService(nodeId, parameter, HttpMethod.POST, "/api/webShell/executeCommand");
        if (rpcResult != null) {
            return rpcResult.getData();
        }

        // 记录执行命令
        if (parameter.getWorkDirectory() == null) {
            parameter.setWorkDirectory(Paths.get("").toAbsolutePath().toString());
        }
        String cmd = parameter.getCmd();
        shellExecuteRecordService.addCmdRecord(parameter.getWorkDirectory(), cmd);

        long begin = System.currentTimeMillis();
        ShellExecuteResult result = new ShellExecuteResult();
        StringBuilder processOutput = new StringBuilder();

        try {
            AtomicBoolean isTimeout = new AtomicBoolean();
            // 创建进程并执行
            Process process = createProcess(parameter);
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
                    log.debug("{}[{}]命令执行中输出: {}",LOG_PREFIX, pid, line);
                }
                result.setExitCode(process.waitFor());
            }
            log.info("{}命令执行完成，输出为: \n{}",LOG_PREFIX, processOutput);
            if (isTimeout.get()) {
                processOutput.append("\n[警告]命令执行超时，已被kill\n");
            }
        } catch (Throwable throwable) {
            log.error("{}命令执行出错",LOG_PREFIX, throwable);
            processOutput.append("命令执行出错:").append(throwable.getMessage());
        }

        result.setOutput(processOutput.toString());
        result.setTime(System.currentTimeMillis() - begin);
        return result;
    }

    /**
     * 调用集群节点接口的服务方法，若指定的节点为自己则返回null
     * @param nodeId    节点id
     * @param body      请求体
     * @param method    请求方法
     * @param url       url
     * @param <R>       返回值
     * @param <T>       参数类型
     * @return          调用返回值，若为null表示为当前节点，未发起远程调用
     */
    private <R, T> CommonResult<R> invokeNodeService(Long nodeId, T body, HttpMethod method, String url) {
        if(nodeId == null || clusterService.getSelf().getId().equals(nodeId)) {
            return null;
        }
        ClusterNodeInfo node = Optional
                .ofNullable(clusterService.getNodeById(nodeId))
                .orElseThrow(() -> new IllegalArgumentException("无效的节点id:" + nodeId));
        RequestParam requestParam = RequestParam.builder()
                .method(method)
                .url(url)
                .body(body)
                .build();

        ResponseEntity<CommonResult<R>> httpCallResult = clusterService.request(
                node.getId(),
                requestParam,
                new ParameterizedTypeReference<>() {}
        );
        return httpCallResult.getBody();
    }

    @Override
    public ShellSessionRecord createSession(Long nodeId, ShellExecuteParameter parameter) throws IOException {
        CommonResult<ShellSessionRecord> rpcResult = invokeNodeService(nodeId, parameter, HttpMethod.POST, "/api/webShell/createSession");
        if (rpcResult != null) {
            return rpcResult.getData();
        }


        Process process = createProcess(parameter);
        Charset charset = Optional.ofNullable(parameter.getCharset())
                .map(Charset::forName)
                .orElse(StandardCharsets.UTF_8);

        OutputStream processOutputStream = process.getOutputStream();
        processOutputStream.write(parameter.getCmd().getBytes(charset));
        ShellSessionRecord session = ShellSessionRecord.builder()
                .host(clusterService.getSelf().getHost())
                .build();
        Date now = new Date();
        session.setId(IdUtil.getId());
        session.setCreateAt(now);
        session.setUpdateAt(now);
        session.setUid(Optional.ofNullable(SecureUtils.getSpringSecurityUser()).map(e -> e.getId().longValue()).orElse((long)User.PUBLIC_USER_ID));

        processMap.put(session.getId(), process);
        sessionMap.put(session.getId(), session);
        BlockStringBuffer outputBuffer = new BlockStringBuffer();
        outputMap.put(session.getId(), outputBuffer);

        String inputTopic = WebShellMQTopic.Prefix.INPUT_STREAM + session.getId();
        String outputTopic = WebShellMQTopic.Prefix.OUTPUT_STREAM + session.getId();
        long inputSubscribeId = mqService.subscribeMessageQueue(inputTopic, WebShellMQTopic.DEFAULT_GROUP, mqMessage -> {
            if (process.isAlive()) {
                try {
                    processOutputStream.write(mqMessage.getBody().toString().getBytes());
                    processOutputStream.flush();
                } catch (Throwable e) {
                    log.error("{}shell会话输入错误, id: {} input: {}",LOG_PREFIX, session.getId(), mqMessage, e);
                }
            }
        });

        threadPool.execute(() -> {
            char[] buffer = new char[8192];
            int cnt;
            try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), charset)){
                while ( (cnt = reader.read(buffer, 0, buffer.length)) != -1) {
                    outputBuffer.append(buffer, 0, cnt);
                    mqService.push(outputTopic, String.valueOf(buffer, 0, cnt));
                }
            } catch (IOException e) {
                log.error("{}读取WebShell进程输出异常, id: {} ", LOG_PREFIX, session.getId(), e);
            } finally {
                mqService.unsubscribeMessageQueue(inputSubscribeId);
                log.info("{}会话{}-{}已结束", LOG_PREFIX, session.getId(), session.getName());
                mqService.destroyQueue(inputTopic);
                mqService.destroyQueue(outputTopic);
            }
        });
        return session;
    }

    @Override
    public Process getProcess(Long sessionId) {
        return processMap.get(sessionId);
    }

    @Override
    public void killLocal(Long sessionId, long forceDelay) {
        Process process = getProcess(sessionId);
        if (process == null) {
            throw new JsonException("会话id" + sessionId + "不存在");
        }
        process.destroy();

        if (forceDelay <= 0) {
            return;
        }

        forceKillPool.execute(() -> {
            if (!process.isAlive()) {
                return;
            }
            try {
                Thread.sleep(forceDelay);
            } catch (InterruptedException e) {
                log.error("{}强制kill等待中断", LOG_PREFIX, e);
            } finally {
                sessionMap.remove(sessionId);
                processMap.remove(sessionId);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        });
    }

    @Override
    public void kill(Long sessionId, long forceDelay) throws IOException {
        boolean inLocal = processMap.get(sessionId) != null;
        if (inLocal) {
            killLocal(sessionId, forceDelay);
        } else {
            rpcManager.call(RPCRequest.builder()
                    .functionName(WebShellRpcFunction.KILL)
                    .param(String.valueOf(forceDelay))
                    .taskId(sessionId)
                    .build(), null);
        }
    }

    @Override
    public List<ShellSessionRecord> getLocalSession() {
        return new ArrayList<>(sessionMap.values());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ShellSessionRecord> getAllSession() throws IOException {
        return rpcManager.call(
                        RPCRequest.builder().functionName(WebShellRpcFunction.LIST_SESSION).build(),
                        List.class,
                        Duration.ofSeconds(5),
                        clusterService.listNodes().size()
                )
                .stream()
                .flatMap(e -> ((List<ShellSessionRecord>)e.getResult()).stream())
                .collect(Collectors.toList());
    }

    @Override
    public void rename(Long sessionId, String newName) {
        ShellSessionRecord session = sessionMap.get(sessionId);
        if (session != null) {
            session.setName(newName);
        }
    }

    @Override
    public String getSessionCurOutput(Long sessionId) {
        // todo 支持集群调用
        BlockStringBuffer buffer = outputMap.get(sessionId);
        if (buffer == null) {
            return null;
        } else {
            return buffer.toString();
        }
    }

    @Override
    public void writeInput(Long sessionId, String input) throws IOException {
        mqService.push(WebShellMQTopic.Prefix.INPUT_STREAM + sessionId, input);
    }

    @Override
    public long subscribeOutput(Long sessionId, Consumer<String> consumer) {
        return mqService.subscribeMessageQueue(WebShellMQTopic.Prefix.OUTPUT_STREAM + sessionId, System.currentTimeMillis() + "", msg -> {
            consumer.accept(msg.getBody().toString());
        });
    }

    private String getLocalOutputLog(Long sessionId) {
        BlockStringBuffer buffer = outputMap.get(sessionId);
        if (buffer != null) {
            return buffer.toString();
        } else {
            return null;
        }
    }

    @Override
    public String getLog(Long sessionId) throws IOException {
        String localResult = getLocalOutputLog(sessionId);
        if (localResult != null) {
            return localResult;
        }

        RPCResponse<String> callResult = rpcManager.call(
                RPCRequest.builder().functionName(WebShellRpcFunction.GET_OUTPUT_LOG).param(sessionId.toString()).build(),
                String.class
        );
        if (callResult != null) {
            return callResult.getResult();
        } else {
            return null;
        }
    }

    @Override
    public void unsubscribeOutput(Long subscribeId) {
        mqService.unsubscribeMessageQueue(subscribeId);
    }
}
