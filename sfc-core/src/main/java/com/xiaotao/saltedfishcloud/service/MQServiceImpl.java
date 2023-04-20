package com.xiaotao.saltedfishcloud.service;


import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@Slf4j
public class MQServiceImpl implements MQService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    private final Map<Long, MessageListener> listenerMap = new ConcurrentHashMap<>();


    @Override
    public void send(String topic, Object msg) {
        redisTemplate.convertAndSend(topic, msg);
    }

    @Override
    public long subscribe(String topic, Consumer<Object> consumer) {
        long id = IdUtil.getId();
        MessageListener listener = (message, pattern) -> consumer.accept(message);
        redisMessageListenerContainer.addMessageListener(listener, new PatternTopic(topic));
        listenerMap.put(id, listener);
        return id;
    }

    @Override
    public void unsubscribe(Long id) {
        MessageListener listener = listenerMap.remove(id);
        if (listener != null) {
            redisMessageListenerContainer.removeMessageListener(listener);
        }
    }
}
