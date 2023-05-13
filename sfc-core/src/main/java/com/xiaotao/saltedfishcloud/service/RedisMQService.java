package com.xiaotao.saltedfishcloud.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.constant.MQTopic;
import com.xiaotao.saltedfishcloud.model.MQMessage;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Service;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@Slf4j
public class RedisMQService implements MQService {
    private final static String LOG_PREFIX = "[消息队列]";
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> stringStreamMessageListenerContainer;

    private final Map<Long, MessageListener> listenerMap = new ConcurrentHashMap<>();

    private final Map<Long, Tuple3<String, String, Subscription>> topicGroupMap = new ConcurrentHashMap<>();

    @Override
    public void createQueue(String topic) {
        // redis不需要特别创建队列，直接push即可，预留该方法以供后续其他消息队列实现使用
    }

    @Override
    public void push(String topic, Object message) throws JsonProcessingException {
        String messageBody = message instanceof CharSequence ? message.toString() : MapperHolder.toJson(message);
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .ofMap(Collections.singletonMap("msg", messageBody))
                .withStreamKey(MQTopic.Prefix.STREAM_PREFIX + topic);
        redisTemplate.opsForStream().add(record);
        log.debug("{}发送消息到队列{} 内容:{}", LOG_PREFIX, topic, messageBody);
    }

    @Override
    public void destroyQueue(String topic) {
        redisTemplate.delete(MQTopic.Prefix.STREAM_PREFIX + topic);
    }

    @Override
    public long subscribeMessageQueue(String topic, String group, Consumer<MQMessage> consumer) {
        redisTemplate.opsForStream().createGroup(MQTopic.Prefix.STREAM_PREFIX + topic, group);
        long id = IdUtil.getId();
        Subscription subscription = stringStreamMessageListenerContainer.receive(
                org.springframework.data.redis.connection.stream.Consumer.from(group, id + ""),
                StreamOffset.create(MQTopic.Prefix.STREAM_PREFIX + topic, ReadOffset.lastConsumed()),
                message -> {
                    log.debug("{}收到队列{}的消息: {}", LOG_PREFIX, topic, message);
                    MQMessage msg = MQMessage.builder()
                            .topic(topic)
                            .body(message.getValue().get("msg"))
                            .build();
                    consumer.accept(msg);
                    redisTemplate.opsForStream().acknowledge(group, message);
                }
        );
        topicGroupMap.put(id, Tuples.of(topic, group, subscription));
        return id;
    }

    @Override
    public void unsubscribeMessageQueue(Long id) {
        Tuple3<String, String, Subscription> tuple = topicGroupMap.get(id);
        redisTemplate.opsForStream().destroyGroup(tuple.getT1(), tuple.getT2());
        stringStreamMessageListenerContainer.remove(tuple.getT3());
    }

    @Override
    public void sendBroadcast(String topic, Object msg) {
        redisTemplate.convertAndSend(topic, msg);
    }

    @Override
    public long subscribeBroadcast(String topic, Consumer<MQMessage> consumer) {
        long id = IdUtil.getId();
        MessageListener listener = (message, pattern) -> consumer.accept(
                MQMessage.builder()
                        .topic(new String(message.getChannel(), StandardCharsets.UTF_8))
                        .body(new String(message.getBody(), StandardCharsets.UTF_8))
                        .build()
        );
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
