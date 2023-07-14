package com.sfc.webshell.service.impl;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
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
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
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
    /**
     * 默认的强制kill延迟
     */
    private final static int DEFAULT_FORCE_KILL_DELAY = 60000;

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
        registerRpcFunction();
    }

    @Override
    public Optional<ShellSessionRecord> getSessionById(Long sessionId) throws IOException {
        ShellSessionRecord localResult = localGetSessionById(sessionId);
        if (localResult != null) {
            return Optional.of(localResult);
        }
        List<RPCResponse<ShellSessionRecord>> callResult = rpcManager.call(RPCRequest.builder()
                        .functionName(WebShellRpcFunction.GET_SESSION_BY_ID)
                        .taskId(sessionId)
                        .build(),
                ShellSessionRecord.class,
                clusterService.listNodes().size()
        );
        return callResult.stream()
                .filter(e -> e.getIsHandled() && e.getIsSuccess())
                .map(RPCResponse::getResult)
                .filter(Objects::nonNull)
                .findAny();
    }

    private ShellSessionRecord localGetSessionById(Long sessionId) {
        return sessionMap.get(sessionId);
    }

    @SuppressWarnings("unchecked")
    private void registerRpcFunction() {
        // 注册获取会话RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.GET_SESSION_BY_ID, request -> {
            Long sessionId = request.getTaskId();
            ShellSessionRecord session = localGetSessionById(sessionId);
            return RPCResponse.success(session);
        });

        // 注册结束进程RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.KILL, request -> {
            Long sessionId = request.getTaskId();
            try {
                killLocal(sessionId, DEFAULT_FORCE_KILL_DELAY);
                return RPCResponse.success(null);
            } catch (JsonException e) {
                return RPCResponse.ignore();
            }
        });

        // 注册获取会话列表RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.LIST_SESSION, request -> RPCResponse.success(new ArrayList<>(sessionMap.values())));

        // 注册获取日志RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.GET_OUTPUT_LOG, request -> {
            Long sessionId = Long.valueOf(request.getParam());
            String localResult = getLocalOutputLog(sessionId);
            if (localResult == null) {
                return RPCResponse.ignore();
            }
            return RPCResponse.success(localResult);
        });

        // 注册重命名会话RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.RENAME_SESSION, request -> {
            Long sessionId = request.getTaskId();
            String newName = request.getParam();
            if(localRename(sessionId, newName)) {
                return RPCResponse.success(null);
            } else {
                return RPCResponse.ignore();
            }
        });

        // 注册移除会话RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.REMOVE, request -> {
            Long sessionId = request.getTaskId();
            ShellSessionRecord session = sessionMap.get(sessionId);
            if (session == null) {
                return RPCResponse.ignore();
            }
            try {
                killLocal(sessionId, DEFAULT_FORCE_KILL_DELAY);
            } catch (Throwable ignore) { } finally {
                sessionMap.remove(sessionId);
                processMap.remove(sessionId);
                outputMap.remove(sessionId);
            }
            return RPCResponse.success(null);
        });

        // 注册重启RPC方法
        rpcManager.registerRpcHandler(WebShellRpcFunction.RESTART, request -> {
            Long sessionId = request.getTaskId();
            ShellSessionRecord session = sessionMap.get(sessionId);
            if (session == null) {
                return RPCResponse.ignore();
            }
            try {
                createProcessFromSession(session);
                return RPCResponse.success(session);
            } catch (Throwable err) {
                log.error("{}重启会话失败: ", LOG_PREFIX, err);
                return RPCResponse.error(err.getMessage());
            }
        });

        rpcManager.registerRpcHandler(WebShellRpcFunction.RESIZE_PTY, request -> {
            Long sessionId = request.getTaskId();
            try {
                Map<String, Integer> param = (Map<String, Integer>)MapperHolder.parseJson(request.getParam(), Map.class);
                Integer rows = param.get("rows");
                Integer cols = param.get("cols");
                boolean isSuccess = doResizePty(sessionId, rows, cols);
                if (isSuccess) {
                    return RPCResponse.success(null);
                } else {
                    return RPCResponse.ignore();
                }
            } catch (IOException e) {
                log.error("{}重置窗口大小反序列化失败: {} ", LOG_PREFIX, request.getParam(), e);
                return RPCResponse.error(e.getMessage());
            } catch (JsonException e) {
                return RPCResponse.error(e.getMessage());
            } catch (Throwable e) {
                log.error("{}重置窗口大小失败: {} ", LOG_PREFIX, request.getParam(), e);
                return RPCResponse.error(e.getMessage());
            }
        });
    }


    /**
     * 创建pty进程
     * @param parameter 参数
     * @return          进程
     */
    private Process createProcess(ShellExecuteParameter parameter) throws IOException {
        String originCmd = Optional.ofNullable(parameter.getShell()).orElse(parameter.getCmd());
        String workDir = Optional.ofNullable(parameter.getWorkDirectory()).orElseGet(() -> Paths.get("").toAbsolutePath().toString());

        Map<String, String> envMap = parameter.getEnv();
        String executablePath = ProcessUtils.resolveCmdExecutablePath(
                workDir,
                originCmd,
                Optional.ofNullable(envMap).map(e -> e.get("PATH")).orElse(null)
        );
        List<String> args = ProcessUtils.parseCommandArgs(originCmd);
        args.set(0, executablePath);

        Map<String, String> processEnv = new HashMap<>();
        if (envMap != null) {
            processEnv.putAll(parameter.getEnv());
        }
        if (parameter.isPty()) {
            PtyProcessBuilder ptyProcessBuilder = new PtyProcessBuilder()
                    .setCommand(args.toArray(new String[0]))
                    .setRedirectErrorStream(true)
                    .setInitialRows(parameter.getInitRows())
                    .setInitialColumns(parameter.getInitCols())
                    .setWindowsAnsiColorEnabled(true)
                    .setDirectory(workDir);
            processEnv.putAll(System.getenv());
            processEnv.put("TERM", "xterm-256color");
            if (envMap != null) {
                processEnv.putAll(envMap);
            }
            ptyProcessBuilder.setEnvironment(processEnv);
            return ptyProcessBuilder.start();
        } else {
            ProcessBuilder processBuilder = new ProcessBuilder()
                    .command(args)
                    .redirectErrorStream(true)
                    .directory(new File(workDir));
            if (envMap != null) {
                processEnv.putAll(envMap);
            }
            processBuilder.environment().putAll(processEnv);
            return processBuilder.start();
        }

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
            long pid = process.pid();
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

    /**
     * 根据初始会话信息创建进程
     * @param session   会话
     * @return          会话（与参数是相同引用）
     */
    protected ShellSessionRecord createProcessFromSession(ShellSessionRecord session) throws IOException {
        // 更新会话初始信息
        ShellExecuteParameter parameter = session.getParameter();
        Date now = new Date();
        if (session.getId() == null) {
            session.setId(IdUtil.getId());
        } else if(processMap.containsKey(session.getId())) {
            throw new IllegalArgumentException("会话已在运行中");
        }
        if (session.getCreateAt() == null) {
            session.setCreateAt(now);
        }
        if (session.getName() == null) {
            session.setName(StringUtils.hasText(parameter.getName()) ? parameter.getName() : "会话" + session.getId());
        }
        session.setRunning(true);
        session.setUpdateAt(now);
        session.setUid(Optional.ofNullable(SecureUtils.getSpringSecurityUser()).map(e -> e.getId().longValue()).orElse((long)User.PUBLIC_USER_ID));

        // 根据参数创建进程
        long begin = System.currentTimeMillis();
        Process process = createProcess(parameter);
        Charset charset = Optional.ofNullable(parameter.getCharset())
                .map(Charset::forName)
                .orElse(StandardCharsets.UTF_8);
        if (process instanceof PtyProcess) {
            WinSize winSize = ((PtyProcess) process).getWinSize();
            session.setRows(winSize.getRows());
            session.setCols(winSize.getColumns());
        }

        // 执行默认cmd
        OutputStream processOutputStream = process.getOutputStream();
        processOutputStream.write(Optional.ofNullable(parameter.getCmd()).orElse("").getBytes(charset));

        // 记录会话相关资源
        BlockStringBuffer outputBuffer = new BlockStringBuffer();
        outputMap.put(session.getId(), outputBuffer);
        processMap.put(session.getId(), process);
        sessionMap.put(session.getId(), session);

        // 进程标准IO消息队列订阅与推送
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
                try {
                    // 等待进程退出
                    session.setExitCode(process.waitFor());
                } catch (InterruptedException ignore) { }
                finally {
                    // 推送进程退出消息
                    log.info("{}会话{}-{}已结束", LOG_PREFIX, session.getId(), session.getName());
                    session.setRunning(false);
                    String exitCodeStr = Optional.ofNullable(session.getExitCode()).map(Object::toString).orElse("未知");
                    String exitMessage = "\r\n\r\n进程已退出，退出代码:" + exitCodeStr + " 运行时长: " + (System.currentTimeMillis() - begin)/1000.0 + "s";
                    outputBuffer.append(exitMessage);
                    try {
                        mqService.push(outputTopic, exitMessage);
                        mqService.sendBroadcast(WebShellMQTopic.Prefix.EXIT_BROADCAST + session.getId(), session.getId());
                        Thread.sleep(2000);
                    } catch (Throwable e) {
                        log.error("{}退出消息推送出错: ", LOG_PREFIX, e);
                    } finally {
                        // 移除相关资源关联
                        processMap.remove(session.getId());
                        mqService.unsubscribeMessageQueue(inputSubscribeId);
                        mqService.destroyQueue(inputTopic);
                        mqService.destroyQueue(outputTopic);

                    }
                }
            }
        });
        return session;
    }

    @Override
    public ShellSessionRecord createSession(Long nodeId, ShellExecuteParameter parameter) throws IOException {
        CommonResult<ShellSessionRecord> rpcResult = invokeNodeService(nodeId, parameter, HttpMethod.POST, "/api/webShell/createSession");
        if (rpcResult != null) {
            return rpcResult.getData();
        }

        ShellSessionRecord session = ShellSessionRecord.builder()
                .host(clusterService.getSelf().getHost())
                .parameter(parameter)
                .build();
        return createProcessFromSession(session);
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
    public void resizePty(Long sessionId, int rows, int cols) throws IOException {
        Map<String, Integer> param = new HashMap<>();
        param.put("rows", rows);
        param.put("cols", cols);
        RPCResponse<Object> result = rpcManager.call(RPCRequest.builder()
                .taskId(sessionId)
                .functionName(WebShellRpcFunction.RESIZE_PTY)
                .param(MapperHolder.toJson(param))
                .build());
        if (!result.getIsSuccess()) {
            throw new JsonException(result.getError());
        }
    }

    private boolean doResizePty(Long sessionId, int rows, int cols) throws IOException {
        ShellSessionRecord session = sessionMap.get(sessionId);
        // 本地不持有该会话
        if (session == null) {
            return false;
        }

        if (!session.getParameter().isPty()) {
            throw new JsonException("不是一个pty模拟终端会话");
        }
        Process process = processMap.get(sessionId);
        if (process == null) {
            throw new JsonException("会话进程已结束");
        }
        ((PtyProcess) process).setWinSize(new WinSize(cols, rows));
        WinSize winSize = ((PtyProcess) process).getWinSize();
        session.setRows(winSize.getRows());
        session.setCols(winSize.getColumns());
        return true;
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

    private boolean localRename(Long sessionId, String newName) {
        ShellSessionRecord session = sessionMap.get(sessionId);
        if (session != null) {
            session.setName(newName);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void rename(Long sessionId, String newName) {
        if (localRename(sessionId, newName)) {
            return;
        }
        try {
            RPCResponse<Object> result = rpcManager.call(RPCRequest.builder().param(newName).taskId(sessionId).functionName(WebShellRpcFunction.RENAME_SESSION).build());
            if (result == null) {
                throw new JsonException("重命名失败, 服务操作无响应");
            }
        } catch (IOException e) {
            throw new JsonException(e.getMessage());
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

    @Override
    public void restart(Long sessionId) throws IOException {
        rpcManager.call(
                RPCRequest.builder().functionName(WebShellRpcFunction.RESTART).taskId(sessionId).build(),
                ShellSessionRecord.class,
                clusterService.listNodes().size()
        ).stream()
                .filter(RPCResponse::getIsHandled)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("找不到shell会话:" + sessionId));
    }

    @Override
    public void remove(Long sessionId) throws IOException {
        rpcManager.call(
                        RPCRequest.builder().functionName(WebShellRpcFunction.REMOVE).taskId(sessionId).build(),
                        Object.class,
                        clusterService.listNodes().size()
                ).stream()
                .filter(RPCResponse::getIsHandled)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("找不到shell会话:" + sessionId));
    }
}
