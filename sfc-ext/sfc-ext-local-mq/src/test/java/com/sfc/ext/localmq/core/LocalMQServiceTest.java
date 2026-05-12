package com.sfc.ext.localmq.core;

import com.sfc.ext.localmq.config.LocalMQProperties;
import com.xiaotao.saltedfishcloud.constant.MQTopicConstants;
import com.xiaotao.saltedfishcloud.enums.MQOffsetStrategy;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
    @DisplayName("测试超出队列最大长度时批量淘汰头部消息")
    void pushExceedsMaxQueueSize() throws Exception {
        LocalMQProperties properties = new LocalMQProperties();
        properties.setMaxQueueSize(5);
        properties.setEvictTargetRatio(0.85);
        try (LocalMQService mqService = new LocalMQService(properties)) {
            // 先 push 10 条消息，超出 maxSize=5，触发批量淘汰到 targetSize=4
            for (int i = 1; i <= 10; i++) {
                mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                        .name("key")
                        .value("msg-" + i)
                        .build());
            }

            // 从头消费，经过批量淘汰后只剩后 4 条（msg-7 ~ msg-10）
            List<String> consumed = new CopyOnWriteArrayList<>();
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g1", MQOffsetStrategy.AT_HEAD, null, record -> {
                consumed.add(record.body().getValue());
            });

            Thread.sleep(300L);
            assertEquals(4, consumed.size());
            assertArrayEquals(new String[]{"msg-7", "msg-8", "msg-9", "msg-10"}, consumed.toArray(new String[0]));
        }
    }

    @Test
    @DisplayName("测试批量淘汰后已有消费组偏移量正确调整并继续消费")
    void pushExceedsMaxQueueSizeWithConsumerOffsetAdjustment() throws Exception {
        LocalMQProperties properties = new LocalMQProperties();
        properties.setMaxQueueSize(5);
        properties.setEvictTargetRatio(0.85);
        try (LocalMQService mqService = new LocalMQService(properties)) {
            // 先订阅消费组 g1，从头消费
            List<String> g1Consumed = new CopyOnWriteArrayList<>();
            mqService.createQueue(MQTopicConstants.CONFIG_CHANGE);
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g1", MQOffsetStrategy.AT_HEAD, null, record -> {
                g1Consumed.add(record.body().getValue());
            });

            // push 10 条消息，超出 maxSize=5，触发批量淘汰到 targetSize=4
            for (int i = 1; i <= 10; i++) {
                mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                        .name("key")
                        .value("msg-" + i)
                        .build());
            }

            // 等待消费者处理完
            Thread.sleep(500L);

            // 消费到的消息数量 >= 最终保留的 4 条
            assertTrue(g1Consumed.size() >= 4,
                    "消费数量应 >= 4, 实际: " + g1Consumed.size());

            // 批量淘汰后最终保留 msg-7 ~ msg-10，一定被消费到
            for (int i = 7; i <= 10; i++) {
                assertTrue(g1Consumed.contains("msg-" + i), "应包含 msg-" + i + ", 实际: " + g1Consumed);
            }

            // 验证队列大小不超过 targetSize=4
            List<String> g2Consumed = new CopyOnWriteArrayList<>();
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g2", MQOffsetStrategy.AT_HEAD, null, record -> {
                g2Consumed.add(record.body().getValue());
            });
            Thread.sleep(300L);
            assertTrue(g2Consumed.size() <= 4,
                    "队列大小应 <= 4, 实际: " + g2Consumed.size());
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

    /**
     * 验证订阅后若队列始终未产生消息，取消最后一个订阅会自动销毁该 topic。
     */
    @Test
    @DisplayName("测试无消息时取消订阅自动销毁topic")
    void unsubscribeLastConsumerAutoDestroyIdleTopicWithoutMessage() throws Exception {
        try (LocalMQService mqService = new LocalMQService()) {
            String topic = MQTopicConstants.CONFIG_CHANGE.getTopic();
            long subscribeId = mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "auto-destroy-no-message-group", record -> {
            });

            mqService.unsubscribeMessageQueue(subscribeId);

            Map<String, LocalMQQueueState> queueStates = getQueueStates(mqService);
            assertFalse(queueStates.containsKey(topic), "队列从未产生消息时，取消最后一个订阅应自动销毁 topic");
        }
    }

    /**
     * 验证队列产生过消息后，取消最后一个订阅不应自动销毁该 topic。
     */
    @Test
    @DisplayName("测试产生过消息后取消订阅不自动销毁topic")
    void unsubscribeLastConsumerAutoDestroyTopicAfterConsumedMessage() throws Exception {
        try (LocalMQService mqService = new LocalMQService()) {
            String topic = MQTopicConstants.CONFIG_CHANGE.getTopic();
            CountDownLatch latch = new CountDownLatch(1);

            long subscribeId = mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "auto-destroy-has-message-group", record -> {
                latch.countDown();
            });

            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("auto-destroy-value")
                    .build());

            assertTrue(latch.await(2, TimeUnit.SECONDS), "订阅者应成功消费消息");
            mqService.unsubscribeMessageQueue(subscribeId);

            Map<String, LocalMQQueueState> queueStates = getQueueStates(mqService);
            assertTrue(queueStates.containsKey(topic), "只要队列产生过消息，取消订阅后不应自动销毁 topic");
        }
    }

    @Test
    @DisplayName("测试订阅topic后，没有产生任何消息时，取消订阅后没有其他任何订阅，会自动销毁该topic")
    void testAutoDestroy() throws Exception {
        try (LocalMQService mqService = new LocalMQService()) {
            String topic = MQTopicConstants.CONFIG_CHANGE.getTopic();
            Map<String, LocalMQQueueState> queueStates = getQueueStates(mqService);
            long subscribeId = mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "auto-destroy-test-group", record -> {
            });
            assertTrue(queueStates.containsKey(topic), "队列从未产生消息时，但存在订阅，topic应该存在");

            // 取消唯一的订阅
            mqService.unsubscribeMessageQueue(subscribeId);
            assertFalse(queueStates.containsKey(topic), "队列从未产生消息时，取消最后一个订阅应自动销毁 topic");


            // 注册2个订阅，但销毁一个，由于还有订阅存在，topic也应该存在
            long id1 = mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g2", record -> {
            });

            long id2 = mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g2", record -> {
            });
            mqService.unsubscribe(id1);
            assertTrue(queueStates.containsKey(topic), "存在订阅时，topic 不应该被销毁");
        }

    }

    /**
     * 验证无订阅时先 push 消息后，g1 从末尾订阅不会消费历史消息；取消 g1 后，g2 从头可消费全部消息。
     */
    @Test
    @DisplayName("测试不同的消费组消费消息后，只要push时没有触发消息淘汰机制，就不应影响另外的消费组能够消费的消息")
    void subscribeFromTailThenUnsubscribeAndConsumeAllFromHead() throws Exception {
        try (LocalMQService mqService = new LocalMQService()) {
            // 1. 无任何订阅时 push 消息
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder()
                    .name("key")
                    .value("value1")
                    .build());

            // 2. g1 从消息末尾开始消费，历史消息不应触发回调
            AtomicInteger g1ConsumeCount = new AtomicInteger(0);
            CountDownLatch g1Latch = new CountDownLatch(3);
            long g1SubscribeId = mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g1", record -> {
                g1ConsumeCount.incrementAndGet();
                g1Latch.countDown();
            });
            Thread.sleep(200L);
            assertEquals(0, g1ConsumeCount.get(), "g1 从尾部订阅时不应消费历史消息");

            // 3. push 3 次消息
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder().name("key").value("value2").build());
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder().name("key").value("value3").build());
            mqService.push(MQTopicConstants.CONFIG_CHANGE, NameValueType.<String>builder().name("key").value("value4").build());
            assertTrue(g1Latch.await(2, TimeUnit.SECONDS), "g1 应消费到新增的 3 条消息");

            // 4. 取消 g1 订阅
            mqService.unsubscribeMessageQueue(g1SubscribeId);

            // 5. g2 从消息头部开始消费，验证可消费总计 4 条消息
            List<String> g2Consumed = new CopyOnWriteArrayList<>();
            CountDownLatch g2Latch = new CountDownLatch(4);
            mqService.subscribeMessageQueue(MQTopicConstants.CONFIG_CHANGE, "g2", MQOffsetStrategy.AT_HEAD, null, record -> {
                g2Consumed.add(record.body().getValue());
                g2Latch.countDown();
            });

            assertTrue(g2Latch.await(2, TimeUnit.SECONDS), "g2 应消费到 4 条消息");
            assertArrayEquals(new String[]{"value1", "value2", "value3", "value4"}, g2Consumed.toArray(new String[0]));
        }
    }

    /**
     * 读取本地队列状态映射。
     *
     * @param mqService 本地消息队列服务
     * @return 队列状态映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, LocalMQQueueState> getQueueStates(LocalMQService mqService) throws Exception {
        Field queueCoordinatorField = LocalMQService.class.getDeclaredField("queueCoordinator");
        queueCoordinatorField.setAccessible(true);
        Object queueCoordinator = queueCoordinatorField.get(mqService);

        Field queueStatesField = LocalMQQueueCoordinator.class.getDeclaredField("queueStates");
        queueStatesField.setAccessible(true);
        return (Map<String, LocalMQQueueState>) queueStatesField.get(queueCoordinator);
    }
}
