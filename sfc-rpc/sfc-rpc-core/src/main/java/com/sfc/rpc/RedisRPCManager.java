package com.sfc.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.rpc.util.RPCServiceProxyUtils;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.utils.ClassUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.LazyLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Redis发布/订阅模型和brpop命令的简易集群RPC管理器
 */
@Slf4j
@Component
public class RedisRPCManager implements RPCManager {
    private final static String ASYNC_TASK_RPC = "ASYNC_TASK_RPC";
    private final static Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    private String logPrefix;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ClusterService clusterService;

    private RedisTemplate<String, Object> redisTemplate;

    private RedisMessageListenerContainer redisMessageListenerContainer;

    private final Map<String, RPCHandler<?>> handlerMap = new ConcurrentHashMap<>();

    private final Map<Class<?>, List<Object>> rpcProxyServiceMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> rpcProxyProvideLazyMap = new ConcurrentHashMap<>();

    @Getter
    private int idCode;

    /**
     * 实例化一个基于Redis的RPC管理器
     */
    @Autowired
    public RedisRPCManager(RedisConnectionFactory redisConnectionFactory, ClusterService clusterService) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.clusterService = clusterService;
        init();
    }

    @Override
    public void registerRPCService(Object obj) {
        Class<?> originClass = obj.getClass();
        Object proxy = RPCServiceProxyUtils.createProxy(obj, this);
        ClassUtils.visitExtendsPath(originClass, clazz -> {
            rpcProxyServiceMap.computeIfAbsent(clazz, key -> new ArrayList<>()).add(proxy);
            return true;
        });
        ClassUtils.visitImplementsPath(originClass, clazz -> {
            rpcProxyServiceMap.computeIfAbsent(clazz, key -> new ArrayList<>()).add(proxy);
            return true;
        });


    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getRPCService(Class<T> clazz) {
        return (T) rpcProxyProvideLazyMap.computeIfAbsent(clazz, key -> {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(clazz);
            enhancer.setClassLoader(clazz.getClassLoader());
            enhancer.setCallback((LazyLoader) () -> {
                List<Object> objects = rpcProxyServiceMap.get(clazz);
                if (objects == null) {
                    throw new IllegalArgumentException("没有注册" + clazz + "的RPC服务");
                }
                if (objects.size() > 1) {
                    throw new IllegalArgumentException("与" + clazz + "关联的RPC服务存在多个");
                }
                return objects.get(0);
            });
            return enhancer.create();
        });
    }

    @Override
    public <T> List<RPCResponse<T>> callAll(RPCRequest request, Class<T> resultType) throws IOException {
        return callAll(request, resultType, DEFAULT_TIMEOUT);
    }

    public void init() {
        initRedisTemplate();
        initRedisMessageListenerContainer();
        this.idCode = this.hashCode() % 10000;
        this.logPrefix = "[Redis RPC@" + idCode + "]";

        registerRedisChannelHandler();
    }

    /**
     * 将redis订阅处理函数拿到的数据转化为RPC请求对象
     */
    private RPCRequest toRpcRequest(Object requestRawObj) throws IOException {
        RPCRequest request;
        if (requestRawObj == null) {
            throw new NullPointerException("请求参数为null");
        } else if (requestRawObj instanceof RPCRequest) {
            request =  (RPCRequest) requestRawObj;
        } else if (requestRawObj instanceof String) {
            request = MapperHolder.parseJson((String) requestRawObj, RPCRequest.class);
        } else {
            throw new IllegalArgumentException("无法识别的RPC请求: " + requestRawObj.getClass());
        }
        return request;
    }

    /**
     * 在Redis订阅RPC请求广播
     */
    protected void registerRedisChannelHandler() {
        redisMessageListenerContainer.addMessageListener((message, pattern) -> {
            try {
                // 解析rpc参数
                Object requestRawObj = redisTemplate.getValueSerializer().deserialize(message.getBody());
                RPCRequest request = toRpcRequest(requestRawObj);
                log.debug("{}收到RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());

                // 调用对应函数的处理器
                RPCHandler<?> rpcHandler = handlerMap.get(request.getFunctionName());
                if (rpcHandler == null) {
                    log.debug("{}忽略了RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());
                    return;
                }

                // 发送响应（由于RPC会广播到每个客户端，节点不一定响应了这个RPC请求，因此还需要判断请求是否被处理）
                RPCResponse<?> response = rpcHandler.handleRpcRequest(request);
                if (response.getIsHandled() != null && response.getIsHandled()) {
                    log.debug("{}响应了RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());
                    sendResponse(request, response);
                } else {
                    log.debug("{}忽略了RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());
                }
            } catch (Exception e) {
                log.error("{}请求处理出错：", logPrefix, e);
            }
        }, new PatternTopic(ASYNC_TASK_RPC));
    }

    /**
     * 发送响应
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sendResponse(RPCRequest request, RPCResponse response) throws JsonProcessingException {
        if (response.getIsSuccess() == null) {
            response.setIsSuccess(true);
        }
        Object result = response.getResult();
        if (result != null) {
            response.setResult(MapperHolder.toJson(result));
        }
        redisTemplate.opsForList().leftPush(request.getResponseKey(), MapperHolder.toJson(response));
        redisTemplate.expire(request.getResponseKey(), DEFAULT_TIMEOUT);
    }

    /**
     * 等待响应
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public  <T> RPCResponse<T> waitResponse(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        Object o = redisTemplate.opsForList().rightPop(request.getResponseKey(), timeout);
        if (o == null) {
            return null;
        }
        if (!(o instanceof String)) {
            throw new IllegalArgumentException("无法处理的redis rpc响应数据类型：" + o.getClass());
        }
        RPCResponse rpcResponse = MapperHolder.parseJson((String) o, RPCResponse.class);
        if (resultType != null && rpcResponse.getResult() != null) {
            rpcResponse.setResult(MapperHolder.parseJson(rpcResponse.getResult().toString(), resultType));
        }
        return rpcResponse;
    }

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

    @Override
    public <T> RPCResponse<T> call(RPCRequest request) throws IOException {
        return call(request, null);
    }

    /**
     * 发起RPC请求
     */
    @Override
    public <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        sendRequest(request);
        return waitResponse(request, resultType, timeout);
    }

    private void sendRequest(RPCRequest request) throws JsonProcessingException {
        request.generateIdIfAbsent();
        redisTemplate.convertAndSend(ASYNC_TASK_RPC,MapperHolder.toJson(request));
    }

    /**
     * 发起RPC请求
     */
    @Override
    public <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType) throws IOException {
        return call(request, resultType, DEFAULT_TIMEOUT);
    }

    /**
     * 注册RPC请求处理器
     * @param functionName  函数名称
     * @param handler       操作器
     */
    @Override
    public <T> void registerRpcHandler(String functionName, RPCHandler<T> handler) {
        if(handlerMap.containsKey(functionName)) {
            log.warn("{}重复注册函数:{}", logPrefix, functionName);
        }
        handlerMap.put(functionName, handler);
    }

    private void initRedisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        serializer.setObjectMapper(MapperHolder.mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setConnectionFactory(redisConnectionFactory);
        template.afterPropertiesSet();
        this.redisTemplate = template;
    }

    private void initRedisMessageListenerContainer() {
        this.redisMessageListenerContainer = new RedisMessageListenerContainer();
        this.redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);
        this.redisMessageListenerContainer.afterPropertiesSet();
        this.redisMessageListenerContainer.start();
    }


}
