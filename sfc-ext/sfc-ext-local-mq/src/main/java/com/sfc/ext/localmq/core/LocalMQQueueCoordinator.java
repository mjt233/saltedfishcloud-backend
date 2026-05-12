package com.sfc.ext.localmq.core;

import com.sfc.ext.localmq.config.LocalMQProperties;
import com.xiaotao.saltedfishcloud.enums.MQOffsetStrategy;
import com.xiaotao.saltedfishcloud.model.MQMessage;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 本地消息队列协调器。
 */
@Slf4j
final class LocalMQQueueCoordinator {
    /**
     * 日志前缀。
     */
    private static final String LOG_PREFIX = "[本地消息队列]";

    /**
     * 广播与队列订阅者 ID 生成器。
     */
    private final AtomicLong subscriberIdGenerator;

    /**
     * 服务关闭状态。
     */
    private final AtomicBoolean serviceShutdown;

    /**
     * 协调器资源是否已释放。
     */
    private final AtomicBoolean released = new AtomicBoolean(false);

    /**
     * 本地队列消息记录 ID 生成器。
     */
    private final AtomicLong queueMessageIdGenerator = new AtomicLong(1);

    /**
     * 队列消费线程 ID 生成器。
     */
    private final AtomicLong queueConsumerThreadIdGenerator = new AtomicLong(1);

    /**
     * 本地消息队列状态映射。
     */
    private final ConcurrentHashMap<String, LocalMQQueueState> queueStates = new ConcurrentHashMap<>();

    /**
     * 队列订阅元数据映射。
     */
    private final ConcurrentHashMap<Long, LocalMQQueueSubscription> queueSubscriptions = new ConcurrentHashMap<>();

    /**
     * 队列消费执行器。
     */
    private final ExecutorService queueConsumerExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("local-mq-consumer-" + queueConsumerThreadIdGenerator.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 队列最大消息数，0 或负数表示不限制。
     */
    private final LocalMQProperties properties;

    /**
     * 队列生命周期监视器。
     */
    private final Object queueLifecycleMonitor = new Object();

    /**
     * 创建本地消息队列协调器。
     *
     * @param subscriberIdGenerator 订阅者 ID 生成器
     * @param serviceShutdown 服务关闭状态
     * @param properties 消息队列属性配置
     */
    LocalMQQueueCoordinator(AtomicLong subscriberIdGenerator, AtomicBoolean serviceShutdown, LocalMQProperties properties) {
        this.subscriberIdGenerator = subscriberIdGenerator;
        this.serviceShutdown = serviceShutdown;
        this.properties = properties;
    }

    /**
     * 创建消息队列。
     *
     * @param topic 队列主题
     */
    void createQueue(String topic) {
        synchronized (queueLifecycleMonitor) {
            ensureServiceRunning("创建新的队列");
            queueStates.computeIfAbsent(topic, this::newQueueState);
        }
    }

    /**
     * 销毁消息队列。
     *
     * @param topic 队列主题
     */
    void destroyQueue(String topic) {
        synchronized (queueLifecycleMonitor) {
            LocalMQQueueState queueState = queueStates.remove(topic);
            if (queueState == null) {
                return;
            }
            List<Long> subscriptionIds = new ArrayList<>();
            queueSubscriptions.forEach((subscriptionId, subscription) -> {
                if (topic.equals(subscription.getTopic())) {
                    subscriptionIds.add(subscriptionId);
                }
            });
            subscriptionIds.forEach(this::unsubscribeMessageQueue);
            clearQueueState(queueState);
        }
    }

    /**
     * 按偏移策略订阅消息队列。
     *
     * @param topic 队列主题
     * @param group 消费组
     * @param offsetStrategy 偏移策略
     * @param offsetPoint 偏移点
     * @param consumer 消费函数
     * @return 订阅者 ID
     */
    long subscribeMessageQueue(String topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint,
                               Consumer<MQMessage> consumer) {
        LocalMQQueueSubscription subscription;
        synchronized (queueLifecycleMonitor) {
            ensureServiceRunning("创建新的队列订阅");
            LocalMQQueueState queueState = queueStates.computeIfAbsent(topic, this::newQueueState);
            synchronized (queueState.getMonitor()) {
                Integer groupOffset = queueState.getGroupOffsets().get(group);
                if (groupOffset == null) {
                    int startOffset = resolveStartOffset(queueState, offsetStrategy, offsetPoint);
                    queueState.getGroupOffsets().put(group, startOffset);
                }
                queueState.getGroupSubscriptionCounts().merge(group, 1, Integer::sum);
                long subscriberId = subscriberIdGenerator.getAndIncrement();
                subscription = new LocalMQQueueSubscription(subscriberId, topic, group, queueState, consumer);
                queueSubscriptions.put(subscriberId, subscription);
            }
        }
        try {
            queueConsumerExecutor.execute(() -> consumeQueue(subscription));
        } catch (RuntimeException e) {
            unsubscribeMessageQueue(subscription.getId());
            throw e;
        }
        return subscription.getId();
    }

    /**
     * 取消消息队列订阅。
     *
     * @param id 订阅者 ID
     * @return 是否移除了订阅
     */
    boolean unsubscribeMessageQueue(Long id) {
        LocalMQQueueSubscription subscription = queueSubscriptions.remove(id);
        if (subscription == null) {
            return false;
        }
        stopQueueSubscription(subscription);
        return true;
    }

    /**
     * 向消息队列推送消息。
     *
     * @param topic 队列主题
     * @param message 消息内容
     */
    void push(String topic, Object message) {
        synchronized (queueLifecycleMonitor) {
            ensureServiceRunning("推送消息");
            LocalMQQueueState queueState = queueStates.computeIfAbsent(topic, this::newQueueState);
            synchronized (queueState.getMonitor()) {
                queueState.getMessages().add(new LocalMQQueueMessageRecord(
                        queueMessageIdGenerator.getAndIncrement(),
                        topic,
                        message
                ));
                evictExcessMessages(queueState);
                queueState.getMonitor().notifyAll();
            }
        }
    }

    /**
     * 关闭协调器并释放队列状态。
     */
    void shutdown() {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        List<LocalMQQueueSubscription> subscriptions;
        List<LocalMQQueueState> states;
        synchronized (queueLifecycleMonitor) {
            subscriptions = new ArrayList<>(queueSubscriptions.values());
            queueSubscriptions.clear();
            states = new ArrayList<>(queueStates.values());
            queueStates.clear();
        }
        subscriptions.forEach(this::stopQueueSubscription);
        states.forEach(this::clearQueueState);
        queueConsumerExecutor.shutdown();
        try {
            if (!queueConsumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                queueConsumerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            queueConsumerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 清理本地队列状态并唤醒所有等待中的消费线程。
     *
     * @param queueState 队列状态
     */
    private void clearQueueState(LocalMQQueueState queueState) {
        synchronized (queueState.getMonitor()) {
            queueState.getMessages().clear();
            queueState.getGroupOffsets().clear();
            queueState.getGroupSubscriptionCounts().clear();
            queueState.getMonitor().notifyAll();
        }
    }

    /**
     * 确保本地消息队列服务尚未关闭。
     *
     * @param action 失败时的操作描述
     */
    private void ensureServiceRunning(String action) {
        if (serviceShutdown.get()) {
            throw new IllegalStateException("本地消息队列服务已关闭，无法" + action);
        }
    }

    /**
     * 创建新的本地队列状态。
     *
     * @param topic 队列主题
     * @return 队列状态
     */
    private LocalMQQueueState newQueueState(String topic) {
        return new LocalMQQueueState(topic, properties.getMaxQueueSize());
    }

    /**
     * 停止本地队列订阅并唤醒等待中的消费者。
     *
     * @param subscription 队列订阅
     */
    private void stopQueueSubscription(LocalMQQueueSubscription subscription) {
        subscription.stop();
        LocalMQQueueState queueState = subscription.getQueueState();
        synchronized (queueLifecycleMonitor) {
            synchronized (queueState.getMonitor()) {
                Integer subscriptionCount = queueState.getGroupSubscriptionCounts().get(subscription.getGroup());
                if (subscriptionCount == null || subscriptionCount <= 1) {
                    queueState.getGroupSubscriptionCounts().remove(subscription.getGroup());
                    queueState.getGroupOffsets().remove(subscription.getGroup());
                } else {
                    queueState.getGroupSubscriptionCounts().put(subscription.getGroup(), subscriptionCount - 1);
                }
                trimConsumedQueueMessages(queueState);
                tryDestroyIdleQueue(subscription.getTopic(), queueState);
                queueState.getMonitor().notifyAll();
            }
        }
    }

    /**
     * 尝试销毁空闲队列主题。
     * <p>
     * 调用方必须先持有 {@link #queueLifecycleMonitor} 与 {@link LocalMQQueueState#getMonitor()} 的监视器。
     *
     * @param topic 队列主题
     * @param queueState 队列状态
     */
    private void tryDestroyIdleQueue(String topic, LocalMQQueueState queueState) {
        if (!queueState.getMessages().isEmpty()) {
            return;
        }
        if (!queueState.getGroupSubscriptionCounts().isEmpty()) {
            return;
        }
        queueStates.remove(topic, queueState);
    }

    /**
     * 基于活动消费组偏移量裁剪已完成消费的队列消息。
     * <p>
     * 调用方必须先持有 {@link LocalMQQueueState#getMonitor()} 的监视器。
     *
     * @param queueState 队列状态
     */
    private void trimConsumedQueueMessages(LocalMQQueueState queueState) {
        if (queueState.getGroupOffsets().isEmpty()) {
            // 无活动消费组时保留历史消息，避免“只要 push 过消息”后又被自动销毁。
            return;
        }
        int minOffset = queueState.getGroupOffsets().values().stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);
        if (minOffset <= 0) {
            return;
        }
        queueState.getMessages().subList(0, minOffset).clear();
        queueState.getGroupOffsets().replaceAll((group, offset) -> offset - minOffset);
    }

    private void evictExcessMessages(LocalMQQueueState queueState) {
        int maxSize = queueState.getMaxSize();
        if (maxSize <= 0) {
            return;
        }
        List<LocalMQQueueMessageRecord> messages = queueState.getMessages();
        int size = messages.size();
        if (size <= maxSize) {
            return;
        }
        int targetSize = (int) (maxSize * properties.getEvictTargetRatio());
        if (targetSize < 1) {
            targetSize = maxSize;
        }
        int excess = size - targetSize;
        messages.subList(0, excess).clear();
        queueState.getGroupOffsets().replaceAll((group, offset) -> Math.max(0, offset - excess));
    }

    /**
     * 解析订阅起始偏移量。
     *
     * @param queueState 队列状态
     * @param offsetStrategy 偏移策略
     * @param offsetPoint 偏移点
     * @return 消息列表中的起始下标
     */
    private int resolveStartOffset(LocalMQQueueState queueState, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint) {
        if (offsetStrategy == MQOffsetStrategy.AT_HEAD) {
            return 0;
        }
        if (offsetStrategy == MQOffsetStrategy.AT_TAIL) {
            return queueState.getMessages().size();
        }
        long messageRecordId = parseMessageRecordId(offsetPoint, queueState.getTopic());
        List<LocalMQQueueMessageRecord> messages = queueState.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId() == messageRecordId) {
                return i;
            }
        }
        throw new IllegalArgumentException("队列主题 " + queueState.getTopic() + " 不存在消息记录: " + offsetPoint);
    }

    /**
     * 解析本地消息记录 ID。
     *
     * @param offsetPoint 偏移点文本
     * @param topic 队列主题
     * @return 消息记录 ID
     */
    private long parseMessageRecordId(@Nullable String offsetPoint, String topic) {
        String recordId = Objects.requireNonNull(offsetPoint, "队列主题 " + topic + " 的 offsetPoint 不能为空");
        try {
            return Long.parseLong(recordId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("队列主题 " + topic + " 的 offsetPoint 非法: " + offsetPoint, e);
        }
    }

    /**
     * 执行本地队列消费循环。
     *
     * @param subscription 队列订阅
     */
    private void consumeQueue(LocalMQQueueSubscription subscription) {
        while (subscription.isActive()) {
            LocalMQQueueMessageRecord messageRecord = waitForNextQueueMessage(subscription);
            if (messageRecord == null) {
                return;
            }
            MQMessage message = MQMessage.builder()
                    .topic(messageRecord.getTopic())
                    .body(messageRecord.getBody())
                    .build();
            safeConsumeQueue(subscription.getTopic(), subscription.getGroup(), subscription.getConsumer(), message);
        }
    }

    /**
     * 等待下一条可消费的队列消息。
     *
     * @param subscription 队列订阅
     * @return 下一条消息，若订阅已停止则返回 null
     */
    private LocalMQQueueMessageRecord waitForNextQueueMessage(LocalMQQueueSubscription subscription) {
        LocalMQQueueState queueState = subscription.getQueueState();
        synchronized (queueState.getMonitor()) {
            while (subscription.isActive()) {
                Integer offset = queueState.getGroupOffsets().get(subscription.getGroup());
                if (offset == null) {
                    return null;
                }
                if (offset < queueState.getMessages().size()) {
                    LocalMQQueueMessageRecord messageRecord = queueState.getMessages().get(offset);
                    queueState.getGroupOffsets().put(subscription.getGroup(), offset + 1);
                    return messageRecord;
                }
                try {
                    queueState.getMonitor().wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 安全执行队列消费函数。
     *
     * @param topic 队列主题
     * @param group 消费组
     * @param consumer 消费函数
     * @param message 队列消息
     */
    private void safeConsumeQueue(String topic, String group, Consumer<MQMessage> consumer, MQMessage message) {
        try {
            consumer.accept(message);
        } catch (Throwable throwable) {
            log.error("{}队列主题 {} 消费组 {} 消费失败", LOG_PREFIX, topic, group, throwable);
        }
    }
}
