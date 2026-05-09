package com.sfc.ext.localmq.core;

import com.xiaotao.saltedfishcloud.enums.MQOffsetStrategy;
import com.xiaotao.saltedfishcloud.model.MQMessage;
import com.xiaotao.saltedfishcloud.model.vo.MQMessageRecord;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import com.xiaotao.saltedfishcloud.service.mq.MQTopic;
import com.xiaotao.saltedfishcloud.utils.ClassUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 基于单 JVM 内存结构的本地消息队列服务实现。
 * <p>
 * 该实现支持广播与队列两种语义，但不提供跨进程、跨节点能力。
 */
public class LocalMQService implements MQService, AutoCloseable {
    /**
     * 广播与队列订阅者 ID 生成器。
     */
    private final AtomicLong subscriberIdGenerator = new AtomicLong(1);

    /**
     * 服务生命周期监视器。
     */
    private final Object lifecycleMonitor = new Object();

    /**
     * 本地队列服务关闭状态。
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 广播订阅注册中心。
     */
    private final LocalMQBroadcastRegistry broadcastRegistry = new LocalMQBroadcastRegistry();

    /**
     * 本地队列协调器。
     */
    private final LocalMQQueueCoordinator queueCoordinator = new LocalMQQueueCoordinator(subscriberIdGenerator, shutdown);

    @Override
    public void sendBroadcast(String topic, Object msg) {
        broadcastRegistry.sendBroadcast(topic, msg);
    }

    @Override
    public <T> void sendBroadcast(MQTopic<T> topic, T msg) {
        sendBroadcast(topic.getTopic(), msg);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> long subscribeBroadcast(MQTopic<T> topic, Consumer<MQMessageRecord<T>> consumer) {
        Class<?> clazz = ClassUtils.getTypeParameterBySuperClass(topic);
        return subscribeBroadcast(topic.getTopic(), message -> consumer.accept(
                new MQMessageRecord<>(message.getTopic(), (T) TypeUtils.convert(clazz, message.getBody()))
        ));
    }

    @Override
    public long subscribeBroadcast(String topic, Consumer<MQMessage> consumer) {
        synchronized (lifecycleMonitor) {
            ensureServiceRunning();
            long subscriberId = subscriberIdGenerator.getAndIncrement();
            broadcastRegistry.subscribe(topic, subscriberId, consumer);
            return subscriberId;
        }
    }

    @Override
    public void unsubscribe(Long id) {
        if (queueCoordinator.unsubscribeMessageQueue(id)) {
            return;
        }
        broadcastRegistry.unsubscribe(id);
    }

    @Override
    public void createQueue(String topic) {
        queueCoordinator.createQueue(topic);
    }

    @Override
    public <T> void createQueue(MQTopic<T> topic) {
        createQueue(topic.getTopic());
    }

    @Override
    public void destroyQueue(String topic) {
        queueCoordinator.destroyQueue(topic);
    }

    @Override
    public void destroyQueue(MQTopic<?> topic) {
        destroyQueue(topic.getTopic());
    }

    @Override
    public long subscribeMessageQueue(String topic, String group, Consumer<MQMessage> consumer) {
        return subscribeMessageQueue(topic, group, MQOffsetStrategy.AT_TAIL, null, consumer);
    }

    @Override
    public <T> long subscribeMessageQueue(MQTopic<T> topic, String group, Consumer<MQMessageRecord<T>> consumer) {
        return subscribeMessageQueue(topic, group, MQOffsetStrategy.AT_TAIL, null, consumer);
    }

    @Override
    public long subscribeMessageQueue(String topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint,
                                      Consumer<MQMessage> consumer) {
        return queueCoordinator.subscribeMessageQueue(topic, group, offsetStrategy, offsetPoint, consumer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> long subscribeMessageQueue(MQTopic<T> topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint,
                                          Consumer<MQMessageRecord<T>> consumer) {
        Class<T> clazz = (Class<T>) ClassUtils.getTypeParameterBySuperClass(topic);
        return subscribeMessageQueue(topic.getTopic(), group, offsetStrategy, offsetPoint, message -> consumer.accept(
                new MQMessageRecord<>(message.getTopic(), convertQueueMessageBody(clazz, message.getBody()))
        ));
    }

    @Override
    public void unsubscribeMessageQueue(Long id) {
        queueCoordinator.unsubscribeMessageQueue(id);
    }

    @Override
    public void push(String topic, Object message) throws IOException {
        queueCoordinator.push(topic, message);
    }

    @Override
    public <T> void push(MQTopic<T> topic, T message) throws IOException {
        push(topic.getTopic(), message);
    }

    /**
     * 显式关闭本地队列服务并释放相关资源。
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        synchronized (lifecycleMonitor) {
            broadcastRegistry.clear();
        }
        queueCoordinator.shutdown();
    }

    @Override
    public void close() throws Exception {
        shutdown();
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
     * 确保本地消息队列服务尚未关闭。
     */
    private void ensureServiceRunning() {
        if (shutdown.get()) {
            throw new IllegalStateException("本地消息队列服务已关闭，无法创建新的广播订阅");
        }
    }
}
