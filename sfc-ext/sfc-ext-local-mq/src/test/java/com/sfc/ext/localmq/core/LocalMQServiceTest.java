package com.sfc.ext.localmq.core;

import com.xiaotao.saltedfishcloud.constant.MQTopicConstants;
import com.xiaotao.saltedfishcloud.enums.MQOffsetStrategy;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
class LocalMQServiceTest {

    @Test
    @DisplayName("测试广播发送与广播订阅")
    void sendBroadcast() throws Exception {
        try(LocalMQService mqService = new LocalMQService()) {
            AtomicInteger consumeNum = new AtomicInteger(0);
            long id1 = mqService.subscribeBroadcast(MQTopicConstants.CONFIG_CHANGE, msg -> {
                consumeNum.incrementAndGet();
                assertEquals("key", msg.body().getName());
                assertEquals("value", msg.body().getValue());
            });
            long id2 = mqService.subscribeBroadcast(MQTopicConstants.CONFIG_CHANGE, msg -> {
                consumeNum.incrementAndGet();
                assertEquals("key", msg.body().getName());
                assertEquals("value", msg.body().getValue());
            });

            // 发送广播，触发2次回调
            mqService.sendBroadcast(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value")
                    .build());

            // 广播发了之后才加的订阅，不应该被触发
            long id3 = mqService.subscribeBroadcast(MQTopicConstants.CONFIG_CHANGE, msg -> {
                consumeNum.incrementAndGet();
                assertEquals("key", msg.body().getName());
                assertEquals("value", msg.body().getValue());
            });
            assertEquals(2, consumeNum.get());


            // 再发一次广播，触发3次回调，计数+3
            mqService.sendBroadcast(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value")
                    .build());
            assertEquals(5, consumeNum.get());


            mqService.unsubscribe(id2);
            // 再发一次广播，触发2次回调，计数+2
            mqService.sendBroadcast(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value")
                    .build());
            assertEquals(7, consumeNum.get());
        }
    }

    @Test
    @DisplayName("测试消息队列发送与订阅")
    void push() throws Exception {
        try(LocalMQService mqService = new LocalMQService()) {
            // 没有订阅的前提下push数据
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value1")
                    .build());

            AtomicInteger consumeNum = new AtomicInteger(0);

            // 从尾部开始消费，历史消息不消费，所以前面push的内容无法消费
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g1", record -> {
                consumeNum.incrementAndGet();
                assertEquals(record.topic(), MQTopicConstants.CONFIG_CHANGE.getTopic());
            });

            // 新push的数据可以消费
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value2")
                    .build());
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value3")
                    .build());
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value4")
                    .build());
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value5")
                    .build());

            // 从头开始消费，历史消息都能拿到
            List<String> historyValue = new CopyOnWriteArrayList<>();
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g2", MQOffsetStrategy.AT_HEAD, null, record -> {
                historyValue.add(record.body().getValue());
            });

            Thread.sleep(300L);
            assertEquals(4, consumeNum.get());
            assertArrayEquals(new String[]{"value1", "value2", "value3", "value4", "value5"}, historyValue.toArray(new String[0]));
        }
    }

    /**
     * 验证同一 topic 同一消费组在存在多个订阅回调时，消息只会被其中一个回调消费一次。
     */
    @Test
    @DisplayName("测试同一topic同一消费组多回调竞争消费")
    void pushWithSameGroupMultiCallbackCompetition() throws Exception {
        try(LocalMQService mqService = new LocalMQService()) {
            int messageCount = 20;
            CountDownLatch latch = new CountDownLatch(messageCount);
            AtomicInteger totalConsumeNum = new AtomicInteger(0);
            Map<String, AtomicInteger> consumeCountByMessage = new ConcurrentHashMap<>();

            // 在同一个topic和消费组 注册2个消费者
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "competition-group", record -> {
                String value = record.body().getValue();
                consumeCountByMessage.computeIfAbsent(value, key -> new AtomicInteger(0)).incrementAndGet();
                totalConsumeNum.incrementAndGet();
                latch.countDown();
            });

            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "competition-group", record -> {
                String value = record.body().getValue();
                consumeCountByMessage.computeIfAbsent(value, key -> new AtomicInteger(0)).incrementAndGet();
                totalConsumeNum.incrementAndGet();
                latch.countDown();
            });

            // 发送20条消息
            for (int i = 1; i <= messageCount; i++) {
                mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                        .name("key")
                        .value("competition-value-" + i)
                        .build());
            }

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            Thread.sleep(200L);

            assertEquals(messageCount, totalConsumeNum.get());
            assertEquals(messageCount, consumeCountByMessage.size());
            consumeCountByMessage.values().forEach(consumeTimes -> assertEquals(1, consumeTimes.get()));
        }
    }
}
