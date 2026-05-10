package com.sfc.ext.localmq.core;

import com.sfc.ext.localmq.config.LocalMQProperties;
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

    @Test
    @DisplayName("测试超出队列最大长度时淘汰头部消息")
    void pushExceedsMaxQueueSize() throws Exception {
        LocalMQProperties properties = new LocalMQProperties();
        properties.setMaxQueueSize(5);
        try (LocalMQService mqService = new LocalMQService(properties)) {
            // 先 push 10 条消息，超出 maxSize=5
            for (int i = 1; i <= 10; i++) {
                mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                        .name("key")
                        .value("msg-" + i)
                        .build());
            }

            // 从头消费，应该只能拿到后 5 条
            List<String> consumed = new CopyOnWriteArrayList<>();
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g1", MQOffsetStrategy.AT_HEAD, null, record -> {
                consumed.add(record.body().getValue());
            });

            Thread.sleep(300L);
            assertEquals(5, consumed.size());
            assertArrayEquals(new String[]{"msg-6", "msg-7", "msg-8", "msg-9", "msg-10"}, consumed.toArray(new String[0]));
        }
    }

    @Test
    @DisplayName("测试淘汰后已有消费组偏移量正确调整并继续消费")
    void pushExceedsMaxQueueSizeWithConsumerOffsetAdjustment() throws Exception {
        LocalMQProperties properties = new LocalMQProperties();
        properties.setMaxQueueSize(5);
        try (LocalMQService mqService = new LocalMQService(properties)) {
            // 先订阅消费组 g1，从头消费
            List<String> g1Consumed = new CopyOnWriteArrayList<>();
            mqService.createQueue(MQTopicConstants.CONFIG_CHANGE);
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g1", MQOffsetStrategy.AT_HEAD, null, record -> {
                g1Consumed.add(record.body().getValue());
            });

            // push 10 条消息，超出 maxSize=5
            for (int i = 1; i <= 10; i++) {
                mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                        .name("key")
                        .value("msg-" + i)
                        .build());
            }

            // 等待消费者处理完
            Thread.sleep(500L);

            // 消费到的消息数量 >= maxSize（消费者可能在队列消息被淘汰前抢到部分中间消息）
            assertTrue(g1Consumed.size() >= 5,
                    "消费数量应 >= 5, 实际: " + g1Consumed.size());

            // 最后 5 条消息（msg-6 ~ msg-10）一定不会被淘汰，一定被消费到
            for (int i = 6; i <= 10; i++) {
                assertTrue(g1Consumed.contains("msg-" + i), "应包含 msg-" + i + ", 实际: " + g1Consumed);
            }

            // 验证队列大小不超过 maxSize: 新消费组从头消费只能拿到 <= maxSize 条
            List<String> g2Consumed = new CopyOnWriteArrayList<>();
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g2", MQOffsetStrategy.AT_HEAD, null, record -> {
                g2Consumed.add(record.body().getValue());
            });
            Thread.sleep(300L);
            assertTrue(g2Consumed.size() <= 5,
                    "队列大小应 <= 5, 实际: " + g2Consumed.size());
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
