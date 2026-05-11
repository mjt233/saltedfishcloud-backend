package com.sfc.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.rpc.exception.RPCIgnoreException;
import com.sfc.rpc.support.RPCContextHolder;
import com.xiaotao.saltedfishcloud.enums.MQOffsetStrategy;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 基于 {@link MQService} 的 RPC 调用器实现。
 * <p>
 * 该实现通过广播主题发送 RPC 请求，并通过按请求 ID 创建的临时消息队列接收响应，
 * 可配合 Redis MQ、本地内存 MQ 等不同 {@link MQService} 实现使用。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sys.service", name = "rpc-provider", havingValue = "mq")
public class MQRPCInvoker implements RPCInvoker {
    /**
     * RPC 请求广播主题名称。
     */
    public static final String RPC_REQUEST_TOPIC = "ASYNC_TASK_RPC";

    /**
     * RPC 响应订阅组前缀。
     */
    public static final String RESPONSE_GROUP_PREFIX = "rpc_response_group_";

    /**
     * 默认 RPC 超时时间。
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    /**
     * RPC 消息队列服务。
     */
    private final MQService mqService;

    /**
     * 集群服务。
     */
    private final ClusterService clusterService;

    /**
     * RPC 共享注册存储。
     */
    private final RPCRegistryStore rpcRegistryStore;

    /**
     * 当前实例日志前缀。
     */
    private String logPrefix;

    /**
     * 当前实例标识码。
     */
    @Getter
    private int idCode;

    /**
     * 创建一个基于消息队列的 RPC 调用器。
     *
     * @param mqService         消息队列服务
     * @param clusterService    集群服务
     * @param rpcRegistryStore  RPC 共享注册存储
     */
    @Autowired
    public MQRPCInvoker(MQService mqService, ClusterService clusterService, RPCRegistryStore rpcRegistryStore) {
        this.mqService = mqService;
        this.clusterService = clusterService;
        this.rpcRegistryStore = rpcRegistryStore;
        init();
    }

    /**
     * 初始化当前调用器并注册请求广播监听器。
     */
    public void init() {
        this.idCode = Math.floorMod(hashCode(), 10000);
        this.logPrefix = "[MQ RPC@" + idCode + "]";
        registerMqRequestHandler();
    }

    /**
     * 将原始请求对象转换为 RPC 请求。
     *
     * @param requestRawObj 原始请求对象
     * @return RPC 请求对象
     * @throws IOException 反序列化失败时抛出
     */
    protected RPCRequest toRpcRequest(Object requestRawObj) throws IOException {
        if (requestRawObj == null) {
            throw new NullPointerException("请求参数为null");
        }
        if (requestRawObj instanceof RPCRequest request) {
            return request;
        }
        if (requestRawObj instanceof String requestJson) {
            return MapperHolder.parseJson(requestJson, RPCRequest.class);
        }
        throw new IllegalArgumentException("无法识别的RPC请求: " + requestRawObj.getClass());
    }

    /**
     * 将原始响应对象转换为指定类型的 RPC 响应。
     *
     * @param responseRawObj 原始响应对象
     * @param resultType     响应结果类型
     * @param <T>            响应结果类型
     * @return RPC 响应
     * @throws IOException 反序列化失败时抛出
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <T> RPCResponse<T> toRpcResponse(Object responseRawObj, Class<T> resultType) throws IOException {
        if (responseRawObj == null) {
            return null;
        }
        final RPCResponse rpcResponse;
        if (responseRawObj instanceof RPCResponse<?> response) {
            rpcResponse = response;
        } else if (responseRawObj instanceof String responseJson) {
            rpcResponse = MapperHolder.parseJson(responseJson, RPCResponse.class);
        } else {
            throw new IllegalArgumentException("无法识别的RPC响应: " + responseRawObj.getClass());
        }
        if (resultType != null && rpcResponse.getResult() != null) {
            String resultJson = rpcResponse.getResult().toString();
            boolean isJsonArray = resultJson.startsWith("[\"") && resultJson.endsWith("]]");
            if ((!resultJson.startsWith("[") || isJsonArray) && Collection.class.isAssignableFrom(resultType)) {
                rpcResponse.setResult(List.of(MapperHolder.withTypeMapper.readValue(resultJson, Object.class)));
            } else {
                rpcResponse.setResult(MapperHolder.parseJson(resultJson, resultType));
            }
        }
        return rpcResponse;
    }

    /**
     * 注册 RPC 请求广播监听器。
     */
    protected void registerMqRequestHandler() {
        mqService.subscribeBroadcast(RPC_REQUEST_TOPIC, message -> {
            try {
                RPCRequest request = toRpcRequest(message.getBody());
                RPCContextHolder.setRequest(request);
                log.debug("{}收到RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());

                RPCHandler<?> rpcHandler = rpcRegistryStore.getRpcHandler(request.getFunctionName());
                if (rpcHandler == null) {
                    log.debug("{}忽略了RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());
                    return;
                }

                RPCResponse<?> response = rpcHandler.handleRpcRequest(request);
                if (Boolean.TRUE.equals(response.getIsHandled())) {
                    log.debug("{}响应了RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());
                    sendResponse(request, response);
                } else {
                    log.debug("{}忽略了RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());
                    if (Boolean.TRUE.equals(request.getIsReportIgnore())) {
                        sendResponse(request, response);
                    }
                }
            } catch (Exception e) {
                log.error("{}请求处理出错：", logPrefix, e);
            } finally {
                RPCContextHolder.remove();
            }
        });
    }

    /**
     * 发送 RPC 响应。
     *
     * @param request  RPC 请求
     * @param response RPC 响应
     * @throws IOException 序列化或发送失败时抛出
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void sendResponse(RPCRequest request, RPCResponse response) throws IOException {
        if (response.getIsSuccess() == null) {
            response.setIsSuccess(true);
        }
        Object result = response.getResult();
        if (result != null) {
            response.setResult(MapperHolder.toJsonWithType(result));
        }
        mqService.push(request.getResponseKey(), MapperHolder.toJsonWithType(response));
    }

    /**
     * 发送 RPC 请求广播。
     *
     * @param request RPC 请求
     * @throws JsonProcessingException 请求序列化失败时抛出
     */
    protected void sendRequest(RPCRequest request) throws JsonProcessingException {
        request.generateIdIfAbsent();
        mqService.sendBroadcast(RPC_REQUEST_TOPIC, MapperHolder.toJson(request));
    }

    /**
     * 订阅指定请求的响应消息队列。
     *
     * @param request RPC 请求
     * @return 响应订阅上下文
     */
    protected ResponseSubscriptionContext subscribeResponseQueue(RPCRequest request) {
        request.generateIdIfAbsent();
        String responseKey = request.getResponseKey();
        String group = RESPONSE_GROUP_PREFIX + request.getRequestId() + '_' + idCode;
        BlockingQueue<Object> responseQueue = new LinkedBlockingQueue<>();
        mqService.createQueue(responseKey);
        long subscriptionId = mqService.subscribeMessageQueue(responseKey, group, MQOffsetStrategy.AT_HEAD, null,
                message -> responseQueue.add(message.getBody()));
        return new ResponseSubscriptionContext(responseKey, subscriptionId, responseQueue);
    }

    /**
     * 等待一个 RPC 响应。
     *
     * @param responseQueue 响应阻塞队列
     * @param resultType    响应结果类型
     * @param timeout       等待超时
     * @param <T>           响应结果类型
     * @return RPC 响应，超时时返回 null
     * @throws IOException 响应解析失败时抛出
     */
    protected <T> RPCResponse<T> waitResponse(BlockingQueue<Object> responseQueue, Class<T> resultType, Duration timeout)
            throws IOException {
        Object responseRawObj;
        try {
            responseRawObj = responseQueue.poll(Math.max(timeout.toMillis(), 1L), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待RPC响应时线程被中断", e);
        }
        return toRpcResponse(responseRawObj, resultType);
    }

    /**
     * 等待一个非忽略型 RPC 响应。
     *
     * @param request       RPC 请求
     * @param responseQueue 响应阻塞队列
     * @param resultType    响应结果类型
     * @param timeout       等待超时
     * @param <T>           响应结果类型
     * @return RPC 响应，超时时返回 null
     * @throws IOException 响应解析失败时抛出
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <T> RPCResponse<T> waitNoIgnoreResponse(RPCRequest request, BlockingQueue<Object> responseQueue,
                                                      Class<T> resultType, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean isIgnore;
        RPCResponse rpcResponse;
        int tryCount = 0;
        int ignoreLimit = resolveNodeCount();
        do {
            Duration remaining = remainingTimeout(deadline);
            if (remaining.isZero()) {
                return null;
            }
            rpcResponse = waitResponse(responseQueue, resultType, remaining);
            if (rpcResponse == null) {
                return null;
            }
            isIgnore = !Boolean.TRUE.equals(rpcResponse.getIsHandled());
        } while (isIgnore && ++tryCount < ignoreLimit);

        if (isIgnore) {
            throw new RPCIgnoreException(request);
        }
        return rpcResponse;
    }

    /**
     * 计算剩余等待时间。
     *
     * @param deadlineNano 截止时间（纳秒）
     * @return 剩余等待时间，最小为零时长
     */
    protected Duration remainingTimeout(long deadlineNano) {
        long remainingNanos = deadlineNano - System.nanoTime();
        if (remainingNanos <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(remainingNanos);
    }

    /**
     * 获取预期集群节点数量。
     *
     * @return 集群节点数量，最小返回 1
     */
    protected int resolveNodeCount() {
        if (clusterService == null) {
            return 1;
        }
        int nodeCount = clusterService.getNodeCount();
        return Math.max(nodeCount, 1);
    }

    /**
     * 获取需要等待的全部响应数量。
     *
     * @return 预期响应数，最小返回 1
     */
    protected int resolveExpectedResponseCount() {
        if (clusterService == null) {
            return 1;
        }
        List<?> nodes = clusterService.listNodes();
        if (nodes == null || nodes.isEmpty()) {
            return 1;
        }
        return nodes.size();
    }

    /**
     * 关闭响应订阅并清理响应队列。
     *
     * @param context 响应订阅上下文
     */
    protected void cleanupResponseQueue(ResponseSubscriptionContext context) {
        try {
            mqService.unsubscribeMessageQueue(context.subscriptionId());
        } finally {
            mqService.destroyQueue(context.responseKey());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<RPCResponse<T>> callAll(RPCRequest request, Class<T> resultType) throws IOException {
        return callAll(request, resultType, DEFAULT_TIMEOUT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<RPCResponse<T>> callAll(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        int expectedCount = resolveExpectedResponseCount();
        List<RPCResponse<T>> responses = new ArrayList<>();
        ResponseSubscriptionContext context = subscribeResponseQueue(request);
        try {
            sendRequest(request);
            long deadline = System.nanoTime() + timeout.toNanos();
            while (responses.size() < expectedCount) {
                Duration remaining = remainingTimeout(deadline);
                if (remaining.isZero()) {
                    return responses;
                }
                RPCResponse<T> response = waitResponse(context.responseQueue(), resultType, remaining);
                if (response == null) {
                    return responses;
                }
                responses.add(response);
            }
            return responses;
        } finally {
            cleanupResponseQueue(context);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        ResponseSubscriptionContext context = subscribeResponseQueue(request);
        try {
            sendRequest(request);
            return waitNoIgnoreResponse(request, context.responseQueue(), resultType, timeout);
        } finally {
            cleanupResponseQueue(context);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType) throws IOException {
        return call(request, resultType, DEFAULT_TIMEOUT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> RPCResponse<T> call(RPCRequest request) throws IOException {
        return call(request, null);
    }

    /**
     * RPC 响应订阅上下文。
     *
     * @param responseKey    响应队列主题
     * @param subscriptionId 响应订阅 ID
     * @param responseQueue  响应阻塞队列
     */
    protected record ResponseSubscriptionContext(String responseKey, long subscriptionId,
                                                 BlockingQueue<Object> responseQueue) {
        /**
         * 创建 RPC 响应订阅上下文。
         */
        protected ResponseSubscriptionContext {
            Objects.requireNonNull(responseKey, "responseKey不能为null");
            Objects.requireNonNull(responseQueue, "responseQueue不能为null");
        }
    }
}



