package com.xiaotao.saltedfishcloud.service.websocket;

import com.sfc.task.AsyncTaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WebSocket 订阅管理器，管理与异步任务日志等相关的订阅生命周期。
 * <p>
 * 维护 {@code subscribedTopic} 映射，确保连接断开时能正确清理所有订阅。
 */
@Slf4j
@Component
public class WebSocketSubscriptionManager {
    private static final String LOG_PREFIX = "[WebSocket]";

    @Autowired
    private AsyncTaskManager asyncTaskManager;

    /**
     * 已订阅的主题：topic -> listenId
     */
    private final Map<String, Long> subscribedTopic = new ConcurrentHashMap<>();

    /**
     * 是否已订阅指定主题。
     *
     * @param topic 主题
     * @return true 表示已订阅
     */
    public boolean isSubscribed(String topic) {
        return subscribedTopic.containsKey(topic);
    }

    /**
     * 订阅异步任务日志。
     *
     * @param topic     消息主题
     * @param taskId    任务 ID
     * @param consumer  日志消息消费者
     */
    public void subscribeTaskLog(String topic, Long taskId, Consumer<String> consumer) {
        long listenId = asyncTaskManager.listenLog(taskId, consumer);
        subscribedTopic.put(topic, listenId);
    }

    /**
     * 取消订阅异步任务日志。
     *
     * @param topic 主题
     */
    public void unsubscribeTaskLog(String topic) {
        Long id = subscribedTopic.remove(topic);
        if (id != null) {
            asyncTaskManager.removeLogListen(id);
        }
    }

    /**
     * 清理所有订阅（连接断开时调用）。
     */
    public void clearAll() {
        subscribedTopic.values().forEach(id -> {
            try {
                asyncTaskManager.removeLogListen(id);
            } catch (Throwable e) {
                log.error("{}主题取消订阅异常：{}", LOG_PREFIX, id, e);
            }
        });
        subscribedTopic.clear();
    }
}
