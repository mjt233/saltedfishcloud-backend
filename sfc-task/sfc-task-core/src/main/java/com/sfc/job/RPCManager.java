package com.sfc.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Redis发布/订阅模型和brpop命令的简易集群RPC管理器
 */
@Slf4j
public class RPCManager {
    private final static String LOG_PREFIX = "[Redis RPC]";
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final static String ASYNC_TASK_RPC = "ASYNC_TASK_RPC";

    private final Map<String, RPCHandler> handlerMap = new ConcurrentHashMap<>();

    /**
     * 实例化一个基于Redis的RPC管理器
     * @param redisTemplate                     用于redis基础操作
     * @param redisMessageListenerContainer     用于redis消息监听
     */
    public RPCManager(RedisTemplate<String, Object> redisTemplate,
                      RedisMessageListenerContainer redisMessageListenerContainer) {
        this.redisTemplate = redisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
        registerRedisChannelHandler();
    }

    /**
     * 将redis订阅处理函数拿到的数据转化为RPC请求对象
     */
    private RPCRequest toRpcRequest(Object requestRawObj) throws JsonProcessingException {
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
     * todo 使用线程池
     */
    protected void registerRedisChannelHandler() {
        redisMessageListenerContainer.addMessageListener((message, pattern) -> {
            try {
                log.debug("{}收到RPC请求, pattern: {}, message: {}", LOG_PREFIX, pattern, message);
                // 解析rpc参数
                Object requestRawObj = redisTemplate.getValueSerializer().deserialize(message.getBody());
                RPCRequest request = toRpcRequest(requestRawObj);

                // 调用对应函数的处理器
                RPCHandler rpcHandler = handlerMap.get(request.getFunctionName());
                if (rpcHandler == null) {
                    throw new IllegalArgumentException("没有关于RPC函数 " + request.getFunctionName() + " 的处理方法");
                }

                // 发送响应（由于RPC会广播到每个客户端，节点不一定响应了这个RPC请求，因此还需要判断请求是否被处理）
                RPCResponse response = rpcHandler.handleRpcRequest(request);
                if (response.getIsHandled() != null && response.getIsHandled()) {
                    sendResponse(request, response);
                }
            } catch (Exception e) {
                log.error("{}请求处理出错：", LOG_PREFIX, e);
            }
        }, new PatternTopic(ASYNC_TASK_RPC));
    }

    /**
     * 发送响应
     */
    private <T> void sendResponse(RPCRequest request, RPCResponse response) throws JsonProcessingException {
        if (response.getIsSuccess() == null) {
            response.setIsSuccess(true);
        }
        Object result = response.getResult();
        if (result != null) {
            response.setResult(MapperHolder.toJson(result));
        }
        redisTemplate.opsForList().leftPush(request.getResponseKey(), MapperHolder.toJson(response));
        redisTemplate.expire(request.getResponseKey(), Duration.ofMinutes(1));
    }

    /**
     * 等待响应
     */
    private <T> RPCResponse<T> waitResponse(RPCRequest request, Class<T> resultType, Duration timeout) throws JsonProcessingException {
        Object o = redisTemplate.opsForList().rightPop(request.getResponseKey(), timeout);
        if (o == null) {
            return null;
        }
        if (!(o instanceof String)) {
            throw new IllegalArgumentException("无法处理的redis rpc响应数据类型：" + o.getClass());
        }
        RPCResponse rpcResponse = MapperHolder.parseJson((String) o, RPCResponse.class);
        if (rpcResponse.getResult() != null) {
            rpcResponse.setResult(MapperHolder.parseJson(rpcResponse.getResult().toString(), resultType));
        }
        return rpcResponse;
    }


    /**
     * 发起RPC请求
     */
    public <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException {
        request.generateIdIfAbsent();
        redisTemplate.convertAndSend(ASYNC_TASK_RPC,MapperHolder.toJson(request));
        return waitResponse(request, resultType, timeout);
    }

    /**
     * 发起RPC请求
     */
    public <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType) throws IOException {
        return call(request, resultType, Duration.ofMinutes(2));
    }

    /**
     * 注册RPC请求处理器
     * @param functionName  函数名称
     * @param handler       操作器
     */
    public void registerRpcHandler(String functionName, RPCHandler handler) {
        handlerMap.put(functionName, handler);
    }


}
