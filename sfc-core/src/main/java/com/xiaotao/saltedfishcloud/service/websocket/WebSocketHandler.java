package com.xiaotao.saltedfishcloud.service.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.task.AsyncTaskManager;
import com.xiaotao.saltedfishcloud.constant.WebSocketConstant;
import com.xiaotao.saltedfishcloud.model.websocket.WebSocketRequest;
import com.xiaotao.saltedfishcloud.model.websocket.WebSocketResponse;
import com.xiaotao.saltedfishcloud.service.mq.MQTopic;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.sfc.task.constants.TaskMQTopic.Get.ASYNC_TASK_LOG;

// todo 使用Spring提供的Websocket框架重构
@Slf4j
@Component
@ServerEndpoint("/api/ws")
public class WebSocketHandler {
    @Autowired
    private AsyncTaskManager asyncTaskManager;

    private static final String LOG_PREFIX = "[WebSocket]";

    /**
     * 单个会话的最大待发送消息数，超过后丢弃最旧消息，避免内存无限增长。
     */
    private static final int MAX_PENDING_MESSAGE = 1000;

    /**
     * 已订阅的主题
     */
    private final Map<String, Long> subscribedTopic = new ConcurrentHashMap<>();

    /**
     * WebSocket 会话发送状态。Tomcat 同一会话不允许并发 sendText，需串行化发送。
     */
    private static final Map<String, SessionSendState> SEND_STATE_MAP = new ConcurrentHashMap<>();

    /**
     * 每个会话独立的发送状态。
     */
    private static class SessionSendState {
        private final Queue<String> queue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger queueSize = new AtomicInteger(0);
        private final AtomicBoolean sending = new AtomicBoolean(false);
    }

    private AsyncTaskManager getAsyncTaskManager() {
        if (asyncTaskManager == null) {
            asyncTaskManager = SpringContextUtils.getContext().getBean(AsyncTaskManager.class);
        }
        return asyncTaskManager;
    }

    /**
     * 获取会话唯一标识。
     *
     * @param session WebSocket 会话
     * @return 会话标识
     */
    private String getSessionKey(Session session) {
        return session.getId();
    }

    /**
     * 将消息加入会话发送队列并尝试触发串行发送。
     *
     * @param session WebSocket 会话
     * @param message 待发送消息
     */
    private void enqueueMessage(Session session, String message) {
        if (!session.isOpen()) {
            clearSessionSendState(session);
            return;
        }
        SessionSendState state = SEND_STATE_MAP.computeIfAbsent(getSessionKey(session), key -> new SessionSendState());
        state.queue.offer(message);
        int newSize = state.queueSize.incrementAndGet();

        if (newSize > MAX_PENDING_MESSAGE) {
            // 背压保护：超出阈值时丢弃最旧日志，优先保证最新进度可见。
            String dropped = state.queue.poll();
            if (dropped != null) {
                state.queueSize.decrementAndGet();
            }
            log.warn("{}会话{}日志发送队列已满，已丢弃最旧消息，当前队列长度:{}", LOG_PREFIX, getSessionKey(session), state.queueSize.get());
        }
        triggerSend(session, state);
    }

    /**
     * 触发会话串行发送流程。
     *
     * @param session WebSocket 会话
     * @param state   会话发送状态
     */
    private void triggerSend(Session session, SessionSendState state) {
        if (!state.sending.compareAndSet(false, true)) {
            return;
        }
        sendNext(session, state);
    }

    /**
     * 发送队列中的下一条消息，发送完成后继续消费后续消息。
     *
     * @param session WebSocket 会话
     * @param state   会话发送状态
     */
    private void sendNext(Session session, SessionSendState state) {
        if (!session.isOpen()) {
            clearSessionSendState(session);
            state.sending.set(false);
            return;
        }

        String next = state.queue.poll();
        if (next == null) {
            state.sending.set(false);
            if (!state.queue.isEmpty() && state.sending.compareAndSet(false, true)) {
                sendNext(session, state);
            }
            return;
        }
        state.queueSize.decrementAndGet();

        session.getAsyncRemote().sendText(next, result -> {
            if (!result.isOK()) {
                log.error("{}向用户{}发送WebSocket消息失败", LOG_PREFIX, getUserName(session), result.getException());
            }
            sendNext(session, state);
        });
    }

    /**
     * 清理会话发送状态，避免连接关闭后残留内存。
     *
     * @param session WebSocket 会话
     */
    private void clearSessionSendState(Session session) {
        SEND_STATE_MAP.remove(getSessionKey(session));
    }

    /**
     * 获取会话对应用户名，用于日志输出。
     *
     * @param session WebSocket 会话
     * @return 用户名，未登录时返回 anonymous
     */
    private String getUserName(Session session) {
        Principal principal = session.getUserPrincipal();
        return principal == null ? "anonymous" : principal.getName();
    }

    /**
     * 收到客户端消息后调用的方法
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        WebSocketRequest request = MapperHolder.parseAsJson(message, WebSocketRequest.class);
        Long taskId = TypeUtils.toLong(request.getData());
        MQTopic<String> mqTopic = ASYNC_TASK_LOG(taskId);
        String topic = mqTopic.getTopic();

        if(WebSocketConstant.Action.SUBSCRIBE_TASK_LOG.equals(request.getAction())) {
            // 订阅异步任务日志动作
            if(subscribedTopic.containsKey(topic)) {
                return;
            }
            long listenId = getAsyncTaskManager().listenLog(taskId, logMessage -> {
                try {
                    String response = MapperHolder.toJson(WebSocketResponse.builder()
                            .id(taskId)
                            .type(WebSocketConstant.Type.ASYNC_TASK_LOG)
                            .data(logMessage)
                            .build()
                    );
                    enqueueMessage(session, response);
                } catch (JsonProcessingException e) {
                    log.error("{}向用户{}的连接发送消息失败，消息:{}...", LOG_PREFIX, getUserName(session), logMessage, e);
                }
            });
            subscribedTopic.put(topic, listenId);
        } else if (WebSocketConstant.Action.UNSUBSCRIBE_TASK_LOG.equals(request.getAction())) {
            // 取消任务日志订阅
            if(!subscribedTopic.containsKey(topic)) {
                return;
            }
            Long id = subscribedTopic.remove(topic);
            getAsyncTaskManager().removeLogListen(id);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        log.debug("用户{}连接WebSocket", getUserName(session));
    }


    @OnClose
    public void onClose(Session session) {
        log.debug("{}用户{}断开WebSocket连接", LOG_PREFIX, getUserName(session));
        subscribedTopic.values().forEach(topic -> {
            try {
                getAsyncTaskManager().removeLogListen(topic);
            } catch (Throwable e) {
                log.error("{}主题{}取消订阅异常：", LOG_PREFIX, topic, e);
            }
        });
        clearSessionSendState(session);
    }
}
