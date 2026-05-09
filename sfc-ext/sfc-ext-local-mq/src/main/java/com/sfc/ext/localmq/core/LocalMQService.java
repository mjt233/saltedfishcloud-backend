package com.sfc.ext.localmq.core;

import com.xiaotao.saltedfishcloud.enums.MQOffsetStrategy;
import com.xiaotao.saltedfishcloud.model.MQMessage;
import com.xiaotao.saltedfishcloud.model.vo.MQMessageRecord;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import com.xiaotao.saltedfishcloud.service.mq.MQTopic;
import com.xiaotao.saltedfishcloud.utils.ClassUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
 * 基于单 JVM 内存结构的本地消息队列服务实现。
 * <p>
 * 该实现支持广播与队列两种语义，但不提供跨进程、跨节点能力。
 */
@Slf4j
public class LocalMQService implements MQService, AutoCloseable {
    /**
     * 日志前缀。
     */
    private static final String LOG_PREFIX = "[本地消息队列]";

    /**
     * 广播与队列订阅者 ID 生成器。
     */
    private final AtomicLong subscriberIdGenerator = new AtomicLong(1);

    /**
     * 本地队列消息记录 ID 生成器。
     */
    private final AtomicLong queueMessageIdGenerator = new AtomicLong(1);

    /**
     * 队列消费线程 ID 生成器。
     */
    private final AtomicLong queueConsumerThreadIdGenerator = new AtomicLong(1);

    /**
     * 广播主题订阅者映射。
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Consumer<MQMessage>>> broadcastSubscribers = new ConcurrentHashMap<>();

    /**
     * 本地消息队列状态映射。
     */
    private final ConcurrentHashMap<String, QueueState> queueStates = new ConcurrentHashMap<>();

    /**
     * 队列订阅元数据映射。
     */
    private final ConcurrentHashMap<Long, QueueSubscription> queueSubscriptions = new ConcurrentHashMap<>();

    /**
     * 订阅 ID 与订阅类型的映射。
     */
    private final ConcurrentHashMap<Long, SubscriptionType> subscriptionTypes = new ConcurrentHashMap<>();

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
     * 队列生命周期监视器。
     */
    private final Object queueLifecycleMonitor = new Object();

    /**
     * 本地队列服务关闭状态。
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 订阅类型。
     */
    private enum SubscriptionType {
        /**
         * 广播订阅。
         */
        BROADCAST,
        /**
         * 队列订阅。
         */
        QUEUE
    }

    /**
     * 发送广播消息。
     *
     * @param topic 主题
     * @param msg 消息内容
     */
    @Override
    public void sendBroadcast(String topic, Object msg) {
        MQMessage mqMessage = MQMessage.builder().topic(topic).body(msg).build();
        ConcurrentHashMap<Long, Consumer<MQMessage>> subscribers = broadcastSubscribers.get(topic);
        if (subscribers == null) {
            return;
        }
        subscribers.values().forEach(consumer -> safeConsumeBroadcast(topic, consumer, mqMessage));
    }

    /**
     * 发送类型化广播消息。
     *
     * @param topic 主题
     * @param msg 消息内容
     * @param <T> 消息类型
     */
    @Override
    public <T> void sendBroadcast(MQTopic<T> topic, T msg) {
        sendBroadcast(topic.getTopic(), msg);
    }

    /**
     * 订阅类型化广播消息。
     *
     * @param topic 主题
     * @param consumer 消费函数
     * @param <T> 消息类型
     * @return 订阅者 ID
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> long subscribeBroadcast(MQTopic<T> topic, Consumer<MQMessageRecord<T>> consumer) {
        Class<?> clazz = ClassUtils.getTypeParameterBySuperClass(topic);
        return subscribeBroadcast(topic.getTopic(), message -> consumer.accept(
                new MQMessageRecord<>(message.getTopic(), (T) TypeUtils.convert(clazz, message.getBody()))
        ));
    }

    /**
     * 订阅广播消息。
     *
     * @param topic 主题
     * @param consumer 消费函数
     * @return 订阅者 ID
     */
    @Override
    public long subscribeBroadcast(String topic, Consumer<MQMessage> consumer) {
        synchronized (queueLifecycleMonitor) {
            ensureServiceRunning("创建新的广播订阅");
            long subscriberId = subscriberIdGenerator.getAndIncrement();
            broadcastSubscribers.computeIfAbsent(topic, key -> new ConcurrentHashMap<>()).put(subscriberId, consumer);
            subscriptionTypes.put(subscriberId, SubscriptionType.BROADCAST);
            return subscriberId;
        }
    }

    /**
     * 取消广播或队列订阅。
     *
     * @param id 订阅者 ID
     */
    @Override
    public void unsubscribe(Long id) {
        SubscriptionType subscriptionType = subscriptionTypes.get(id);
        if (subscriptionType == null) {
            return;
        }
        if (subscriptionType == SubscriptionType.QUEUE) {
            unsubscribeMessageQueue(id);
            return;
        }
        broadcastSubscribers.values().forEach(subscribers -> subscribers.remove(id));
        subscriptionTypes.remove(id);
    }

    /**
     * 创建消息队列。
     *
     * @param topic 队列主题
     */
    @Override
    public void createQueue(String topic) {
        synchronized (queueLifecycleMonitor) {
            ensureServiceRunning("创建新的队列");
            queueStates.computeIfAbsent(topic, this::newQueueState);
        }
    }

    /**
     * 创建类型化消息队列。
     *
     * @param topic 队列主题
     * @param <T> 消息类型
     */
    @Override
    public <T> void createQueue(MQTopic<T> topic) {
        createQueue(topic.getTopic());
    }

    /**
     * 销毁消息队列。
     *
     * @param topic 队列主题
     */
    @Override
    public void destroyQueue(String topic) {
        synchronized (queueLifecycleMonitor) {
            QueueState queueState = queueStates.remove(topic);
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
     * 销毁类型化消息队列。
     *
     * @param topic 队列主题
     */
    @Override
    public void destroyQueue(MQTopic<?> topic) {
        destroyQueue(topic.getTopic());
    }

    /**
     * 订阅消息队列。
     *
     * @param topic 队列主题
     * @param group 消费组
     * @param consumer 消费函数
     * @return 订阅者 ID
     */
    @Override
    public long subscribeMessageQueue(String topic, String group, Consumer<MQMessage> consumer) {
        return subscribeMessageQueue(topic, group, MQOffsetStrategy.AT_TAIL, null, consumer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> long subscribeMessageQueue(MQTopic<T> topic, String group, Consumer<MQMessageRecord<T>> consumer) {
        return subscribeMessageQueue(topic, group, MQOffsetStrategy.AT_TAIL, null, consumer);
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
    @Override
    public long subscribeMessageQueue(String topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint, Consumer<MQMessage> consumer) {
        QueueSubscription subscription;
        synchronized (queueLifecycleMonitor) {
            if (shutdown.get()) {
                throw new IllegalStateException("本地消息队列服务已关闭，无法创建新的队列订阅");
            }
            QueueState queueState = queueStates.computeIfAbsent(topic, this::newQueueState);
            synchronized (queueState.getMonitor()) {
                ensureQueueGroupNotSubscribed(topic, group);
                int startOffset = resolveStartOffset(queueState, offsetStrategy, offsetPoint);
                queueState.getGroupOffsets().put(group, startOffset);
                long subscriberId = subscriberIdGenerator.getAndIncrement();
                subscription = new QueueSubscription(subscriberId, topic, group, queueState, consumer);
                queueSubscriptions.put(subscriberId, subscription);
                subscriptionTypes.put(subscriberId, SubscriptionType.QUEUE);
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
     * 按偏移策略订阅类型化消息队列。
     *
     * @param topic 队列主题
     * @param group 消费组
     * @param offsetStrategy 偏移策略
     * @param offsetPoint 偏移点
     * @param consumer 消费函数
     * @param <T> 消息类型
     * @return 订阅者 ID
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> long subscribeMessageQueue(MQTopic<T> topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint, Consumer<MQMessageRecord<T>> consumer) {
        Class<T> clazz = (Class<T>) ClassUtils.getTypeParameterBySuperClass(topic);
        return subscribeMessageQueue(topic.getTopic(), group, offsetStrategy, offsetPoint, message -> consumer.accept(
                new MQMessageRecord<>(message.getTopic(), convertQueueMessageBody(clazz, message.getBody()))
        ));
    }

    /**
     * 取消消息队列订阅。
     *
     * @param id 订阅者 ID
     */
    @Override
    public void unsubscribeMessageQueue(Long id) {
        QueueSubscription subscription = queueSubscriptions.remove(id);
        if (subscription == null) {
            return;
        }
        subscriptionTypes.remove(id);
        stopQueueSubscription(subscription);
    }

    /**
     * 向消息队列推送消息。
     *
     * @param topic 队列主题
     * @param message 消息内容
     * @throws IOException IO 异常
     */
    @Override
    public void push(String topic, Object message) throws IOException {
        synchronized (queueLifecycleMonitor) {
            ensureServiceRunning("推送消息");
            QueueState queueState = queueStates.computeIfAbsent(topic, this::newQueueState);
            synchronized (queueState.getMonitor()) {
                queueState.getMessages().add(new LocalQueueMessageRecord(
                        queueMessageIdGenerator.getAndIncrement(),
                        topic,
                        message
                ));
                queueState.getMonitor().notifyAll();
            }
        }
    }

    /**
     * 向类型化消息队列推送消息。
     *
     * @param topic 队列主题
     * @param message 消息内容
     * @param <T> 消息类型
     * @throws IOException IO 异常
     */
    @Override
    public <T> void push(MQTopic<T> topic, T message) throws IOException {
        push(topic.getTopic(), message);
    }

    /**
     * 显式关闭本地队列消费执行器并释放队列状态。
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        List<QueueSubscription> subscriptions;
        List<QueueState> states;
        synchronized (queueLifecycleMonitor) {
            subscriptions = new ArrayList<>(queueSubscriptions.values());
            queueSubscriptions.clear();
            states = new ArrayList<>(queueStates.values());
            queueStates.clear();
            broadcastSubscribers.clear();
        }
        subscriptions.forEach(subscription -> {
            subscriptionTypes.remove(subscription.getId());
            stopQueueSubscription(subscription);
        });
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

    @Override
    public void close() throws Exception {
        this.shutdown();
    }

    /**
     * 清理本地队列状态并唤醒所有等待中的消费线程。
     *
     * @param queueState 队列状态
     */
    private void clearQueueState(QueueState queueState) {
        synchronized (queueState.getMonitor()) {
            queueState.getMessages().clear();
            queueState.getGroupOffsets().clear();
            queueState.getMonitor().notifyAll();
        }
    }

    /**
     * 确保本地消息队列服务尚未关闭。
     *
     * @param action 失败时的操作描述
     */
    private void ensureServiceRunning(String action) {
        if (shutdown.get()) {
            throw new IllegalStateException("本地消息队列服务已关闭，无法" + action);
        }
    }

    /**
     * 创建新的本地队列状态。
     *
     * @param topic 队列主题
     * @return 队列状态
     */
    private QueueState newQueueState(String topic) {
        return new QueueState(topic);
    }

    /**
     * 确保同一主题消费组不存在活动中的本地订阅。
     *
     * @param topic 队列主题
     * @param group 消费组
     */
    private void ensureQueueGroupNotSubscribed(String topic, String group) {
        for (QueueSubscription subscription : queueSubscriptions.values()) {
            if (subscription.isActive() && topic.equals(subscription.getTopic()) && group.equals(subscription.getGroup())) {
                throw new IllegalStateException("队列主题 " + topic + " 的消费组 " + group + " 已存在活动订阅");
            }
        }
    }

    /**
     * 停止本地队列订阅并唤醒等待中的消费者。
     *
     * @param subscription 队列订阅
     */
    private void stopQueueSubscription(QueueSubscription subscription) {
        subscription.stop();
        synchronized (subscription.getQueueState().getMonitor()) {
            subscription.getQueueState().getGroupOffsets().remove(subscription.getGroup());
            trimConsumedQueueMessages(subscription.getQueueState());
            subscription.getQueueState().getMonitor().notifyAll();
        }
    }

    /**
     * 基于活动消费组偏移量裁剪已完成消费的队列消息。
     * <p>
     * 调用方必须先持有 {@link QueueState#getMonitor()} 的监视器。
     *
     * @param queueState 队列状态
     */
    private void trimConsumedQueueMessages(QueueState queueState) {
        if (queueState.getGroupOffsets().isEmpty()) {
            queueState.getMessages().clear();
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

    /**
     * 解析订阅起始偏移量。
     *
     * @param queueState 队列状态
     * @param offsetStrategy 偏移策略
     * @param offsetPoint 偏移点
     * @return 消息列表中的起始下标
     */
    private int resolveStartOffset(QueueState queueState, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint) {
        if (offsetStrategy == MQOffsetStrategy.AT_HEAD) {
            return 0;
        }
        if (offsetStrategy == MQOffsetStrategy.AT_TAIL) {
            return queueState.getMessages().size();
        }
        long messageRecordId = parseMessageRecordId(offsetPoint, queueState.getTopic());
        List<LocalQueueMessageRecord> messages = queueState.getMessages();
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
    private void consumeQueue(QueueSubscription subscription) {
        while (subscription.isActive()) {
            LocalQueueMessageRecord messageRecord = waitForNextQueueMessage(subscription);
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
    private LocalQueueMessageRecord waitForNextQueueMessage(QueueSubscription subscription) {
        QueueState queueState = subscription.getQueueState();
        synchronized (queueState.getMonitor()) {
            while (subscription.isActive()) {
                Integer offset = queueState.getGroupOffsets().get(subscription.getGroup());
                if (offset == null) {
                    return null;
                }
                if (offset < queueState.getMessages().size()) {
                    LocalQueueMessageRecord messageRecord = queueState.getMessages().get(offset);
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
     * 将队列消息转换为类型化消息体。
     *
     * @param targetType 目标类型
     * @param body 原始消息体
     * @param <T> 目标类型
     * @return 转换后的消息体
     */
    private <T> T convertQueueMessageBody(Class<T> targetType, Object body) {
        if (body == null) {
            return null;
        }
        try {
            if (TypeUtils.isSupportConvert(targetType) || targetType.isAssignableFrom(body.getClass())) {
                return TypeUtils.convert(targetType, body);
            }
            return MapperHolder.parseAsJson(body, targetType);
        } catch (IOException e) {
            throw new IllegalStateException("无法将消息体转换为 " + targetType.getName(), e);
        }
    }

    /**
     * 安全执行广播消费函数。
     *
     * @param topic 主题
     * @param consumer 消费函数
     * @param message 广播消息
     */
    private void safeConsumeBroadcast(String topic, Consumer<MQMessage> consumer, MQMessage message) {
        try {
            consumer.accept(message);
        } catch (Throwable throwable) {
            log.error("{}广播主题 {} 消费失败", LOG_PREFIX, topic, throwable);
        }
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

    /**
     * 本地队列消息记录。
     */
    @Getter
    private static final class LocalQueueMessageRecord {
        /**
         * 消息记录 ID。
         */
        private final long id;

        /**
         * 队列主题。
         */
        private final String topic;

        /**
         * 消息内容。
         */
        private final Object body;

        /**
         * 创建本地队列消息记录。
         *
         * @param id 消息记录 ID
         * @param topic 队列主题
         * @param body 消息内容
         */
        private LocalQueueMessageRecord(long id, String topic, Object body) {
            this.id = id;
            this.topic = topic;
            this.body = body;
        }
    }

    /**
     * 本地队列状态。
     */
    @Getter
    private static final class QueueState {
        /**
         * 队列主题。
         */
        private final String topic;

        /**
         * 队列消息列表。
         */
        private final List<LocalQueueMessageRecord> messages = new ArrayList<>();

        /**
         * 消费组偏移量。
         */
        private final ConcurrentHashMap<String, Integer> groupOffsets = new ConcurrentHashMap<>();

        /**
         * 队列监视器。
         */
        private final Object monitor = new Object();

        /**
         * 创建本地队列状态。
         *
         * @param topic 队列主题
         */
        private QueueState(String topic) {
            this.topic = topic;
        }
    }

    /**
     * 队列订阅元数据。
     */
    @Getter
    private static final class QueueSubscription {
        /**
         * 订阅 ID。
         */
        private final long id;

        /**
         * 队列主题。
         */
        private final String topic;

        /**
         * 消费组。
         */
        private final String group;

        /**
         * 关联的队列状态。
         */
        private final QueueState queueState;

        /**
         * 消费函数。
         */
        private final Consumer<MQMessage> consumer;

        /**
         * 订阅活动状态。
         */
        private final AtomicBoolean active = new AtomicBoolean(true);

        /**
         * 创建队列订阅元数据。
         *
         * @param id 订阅 ID
         * @param topic 队列主题
         * @param group 消费组
         * @param queueState 队列状态
         * @param consumer 消费函数
         */
        private QueueSubscription(long id, String topic, String group, QueueState queueState, Consumer<MQMessage> consumer) {
            this.id = id;
            this.topic = topic;
            this.group = group;
            this.queueState = queueState;
            this.consumer = consumer;
        }

        /**
         * 判断订阅是否仍处于活动状态。
         *
         * @return 订阅活动状态
         */
        private boolean isActive() {
            return active.get();
        }

        /**
         * 停止当前订阅。
         */
        private void stop() {
            active.set(false);
        }
    }
}
