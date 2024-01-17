package com.sfc.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.rpc.exception.RPCIgnoreException;
import com.sfc.rpc.support.RPCMethodActionHandler;
import com.sfc.rpc.support.RPCContextHolder;
import com.sfc.rpc.util.RPCActionDefinitionUtils;
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
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Redis发布/订阅模型和brpop命令的简易集群RPC管理器
 * todo 支持身份鉴权
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

    private final Map<Class<?>, List<Object>> rpcClientMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> rpcClientLazyMap = new ConcurrentHashMap<>();

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
        Class<?> clazz = obj.getClass();
        RPCActionDefinitionUtils.getRPCActionDefinition(clazz).forEach((id, def) -> {
            this.registerRpcHandler(def.getFullFunctionName(), new RPCMethodActionHandler<>(obj, def));
        });

    }

    @Override
    public void registerRPCClient(Class<?> clazz) {
        Object client = RPCActionDefinitionUtils.createRPCClient(clazz, this);
        ClassUtils.visitExtendsPath(clazz, parentClass -> {
            rpcClientMap.computeIfAbsent(parentClass, key -> new ArrayList<>()).add(client);
            return true;
        });
        ClassUtils.visitImplementsPath(clazz, interfaceClass -> {
            rpcClientMap.computeIfAbsent(interfaceClass, key -> new ArrayList<>()).add(client);
            return true;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getRPCClient(Class<T> clazz) {
        return (T) rpcClientLazyMap.computeIfAbsent(clazz, key -> {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(clazz);
            enhancer.setClassLoader(clazz.getClassLoader());
            enhancer.setCallback((LazyLoader) () -> {
                List<Object> objects = rpcClientMap.get(clazz);
                if (objects == null) {
                    T client = RPCActionDefinitionUtils.createRPCClient(clazz, this);
                    List<Object> clientList = new ArrayList<>();
                    clientList.add(client);
                    rpcClientMap.put(clazz, clientList);
                    return client;
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
                RPCContextHolder.setRequest(request);
                log.debug("{}收到RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());

                // 调用对应函数的处理器
                RPCHandler<?> rpcHandler = handlerMap.get(request.getFunctionName());
                if (rpcHandler == null) {
                    log.debug("{}忽略了RPC请求: {}, request id: {}", logPrefix, request.getFunctionName(), request.getRequestId());
                    return;
                }

                // 发送响应
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
     * 发送响应
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sendResponse(RPCRequest request, RPCResponse response) throws JsonProcessingException {
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
     * 等待响应。若拿到的是“已忽略”响应则会继续等待，直到拿到“已处理”的响应
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T> RPCResponse<T> waitNoIgnoreResponse(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        boolean isIgnore;
        RPCResponse rpcResponse;
        int tryCount = 0;
        do {
            // 尝试等待一个响应
            rpcResponse = waitResponse(request, resultType, timeout);
            // null响应超时，直接返回
            if (rpcResponse == null) {
                return null;
            }
            // 判定是否为一个“已忽略”响应，如果是且是第一次拿到“已忽略”，则获取当前集群节点数量，继续等待非“已忽略”的响应。
            // 直到获取到非”已忽略“响应或收到的忽略数量与节点数量相等
            isIgnore = !Boolean.TRUE.equals(rpcResponse.getIsHandled());
        } while (isIgnore && ++tryCount <= clusterService.getNodeCount());

        if (isIgnore) {
            throw new RPCIgnoreException(request);
        }
        return rpcResponse;
    }

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
        return waitNoIgnoreResponse(request, resultType, timeout);
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
