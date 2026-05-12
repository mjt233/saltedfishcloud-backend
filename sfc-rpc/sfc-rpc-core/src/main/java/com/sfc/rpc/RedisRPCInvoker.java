package com.sfc.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.rpc.exception.RPCIgnoreException;
import com.sfc.rpc.support.RPCContextHolder;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 基于Redis发布订阅模型的RPC调用器实现。
 * <p>
 * 该实现负责：
 * <ul>
 *     <li>向Redis广播RPC请求</li>
 *     <li>监听RPC请求并调用已注册的处理器</li>
 *     <li>等待并解析RPC响应</li>
 * </ul>
 * 处理器与客户端代理的注册关系由{@link RPCRegistryStore}维护。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sys.service", name = "rpc-provider", havingValue = "redis", matchIfMissing = true)
public class RedisRPCInvoker implements RPCInvoker {
    /**
     * RPC广播主题名称。
     */
    public static final String ASYNC_TASK_RPC = "ASYNC_TASK_RPC";

    /**
     * 默认RPC超时时间。
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    /**
     * 当前实例日志前缀。
     */
    private String logPrefix;

    /**
     * Redis连接工厂。
     */
    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * 集群服务。
     */
    private final ClusterService clusterService;

    /**
     * RPC共享注册存储。
     */
    private final RPCRegistryStore rpcRegistryStore;

    /**
     * Redis模板。
     */
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis消息监听容器。
     */
    private RedisMessageListenerContainer redisMessageListenerContainer;

    /**
     * 当前实例标识码。
     */
    @Getter
    private int idCode;

    /**
     * 实例化一个基于Redis的RPC调用器。
     *
     * @param redisConnectionFactory Redis连接工厂
     * @param clusterService         集群服务
     * @param rpcRegistryStore       RPC共享注册存储
     */
    @Autowired
    public RedisRPCInvoker(RedisConnectionFactory redisConnectionFactory, ClusterService clusterService, RPCRegistryStore rpcRegistryStore) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.clusterService = clusterService;
        this.rpcRegistryStore = rpcRegistryStore;
        init();
    }

    /**
     * 初始化Redis调用器。
     */
    public void init() {
        initRedisTemplate();
        initRedisMessageListenerContainer();
        this.idCode = this.hashCode() % 10000;
        this.logPrefix = "[Redis RPC@" + idCode + "]";
        registerRedisChannelHandler();
    }

    /**
     * 将Redis订阅消息转换为RPC请求对象。
     *
     * @param requestRawObj Redis原始消息对象
     * @return RPC请求对象
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
     * 在Redis上注册RPC请求广播监听器。
     */
    protected void registerRedisChannelHandler() {
        redisMessageListenerContainer.addMessageListener((message, ignoredPattern) -> {
            try {
                Object requestRawObj = redisTemplate.getValueSerializer().deserialize(message.getBody());
                RPCRequest request = toRpcRequest(requestRawObj);
                RPCContextHolder.setRequest(request);
                log.debug("{}收到RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());

                RPCHandler<?> rpcHandler = rpcRegistryStore.getRpcHandler(request.getFunctionName());
                if (rpcHandler == null) {
                    log.debug("{}忽略了RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());
                    return;
                }

                RPCResponse<?> response = rpcHandler.handleRpcRequest(request);
                if (response.getIsHandled() != null && response.getIsHandled()) {
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
        }, new PatternTopic(ASYNC_TASK_RPC));
    }

    /**
     * 发送RPC响应。
     *
     * @param request  RPC请求
     * @param response RPC响应
     * @throws JsonProcessingException 序列化失败时抛出
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void sendResponse(RPCRequest request, RPCResponse response) throws JsonProcessingException {
        if (response.getIsSuccess() == null) {
            response.setIsSuccess(true);
        }
        Object result = response.getResult();
        if (result != null) {
            response.setResult(MapperHolder.toJsonWithType(result));
        }
        redisTemplate.opsForList().leftPush(request.getResponseKey(), MapperHolder.toJsonWithType(response));
        redisTemplate.expire(request.getResponseKey(), DEFAULT_TIMEOUT);
    }

    /**
     * 等待一个非忽略型响应。
     *
     * @param request    RPC请求
     * @param resultType 响应结果类型
     * @param timeout    超时时间
     * @param <T>        响应结果类型
     * @return RPC响应，超时时返回null
     * @throws IOException 解析响应失败时抛出
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T> RPCResponse<T> waitNoIgnoreResponse(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        boolean isIgnore;
        RPCResponse rpcResponse;
        int tryCount = 0;
        do {
            rpcResponse = waitResponse(request, resultType, timeout);
            if (rpcResponse == null) {
                return null;
            }
            isIgnore = !Boolean.TRUE.equals(rpcResponse.getIsHandled());
        } while (isIgnore && ++tryCount <= clusterService.getNodeCount());

        if (isIgnore) {
            throw new RPCIgnoreException(request);
        }
        return rpcResponse;
    }

    /**
     * 等待一个RPC响应。
     *
     * @param request    RPC请求
     * @param resultType 响应结果类型
     * @param timeout    超时时间
     * @param <T>        响应结果类型
     * @return RPC响应，超时时返回null
     * @throws IOException 解析响应失败时抛出
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T> RPCResponse<T> waitResponse(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        Object o = redisTemplate.opsForList().rightPop(request.getResponseKey(), timeout);
        if (o == null) {
            return null;
        }
        if (!(o instanceof String)) {
            throw new IllegalArgumentException("无法处理的redis rpc响应数据类型：" + o.getClass());
        }
        RPCResponse rpcResponse = MapperHolder.parseJson((String) o, RPCResponse.class);
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
        int exceptCount = clusterService.listNodes().size();
        if (exceptCount <= 0) {
            throw new IllegalArgumentException("rpc exceptCount 必须 > 0");
        }
        List<RPCResponse<T>> res = new ArrayList<>();
        sendRequest(request);
        int getCount = 0;
        do {
            RPCResponse<T> response = waitResponse(request, resultType, timeout);
            if (response != null) {
                res.add(response);
                getCount++;
            } else {
                return res;
            }
        } while (exceptCount > getCount);
        return res;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> RPCResponse<T> call(RPCRequest request) throws IOException {
        return call(request, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        sendRequest(request);
        return waitNoIgnoreResponse(request, resultType, timeout);
    }

    /**
     * 发送RPC请求。
     *
     * @param request RPC请求
     * @throws JsonProcessingException 序列化失败时抛出
     */
    protected void sendRequest(RPCRequest request) throws JsonProcessingException {
        request.generateIdIfAbsent();
        redisTemplate.convertAndSend(ASYNC_TASK_RPC, MapperHolder.toJson(request));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType) throws IOException {
        return call(request, resultType, DEFAULT_TIMEOUT);
    }

    /**
     * 初始化Redis模板。
     */
    protected void initRedisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(MapperHolder.mapper, Object.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setConnectionFactory(redisConnectionFactory);
        template.afterPropertiesSet();
        this.redisTemplate = template;
    }

    /**
     * 初始化Redis消息监听容器。
     */
    protected void initRedisMessageListenerContainer() {
        this.redisMessageListenerContainer = new RedisMessageListenerContainer();
        this.redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);
        this.redisMessageListenerContainer.afterPropertiesSet();
        this.redisMessageListenerContainer.start();
    }
}


