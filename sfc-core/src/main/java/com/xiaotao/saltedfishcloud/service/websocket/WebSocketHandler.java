package com.xiaotao.saltedfishcloud.service.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.constant.MQTopic;
import com.sfc.constant.WebSocketConstant;
import com.sfc.task.AsyncTaskManager;
import com.xiaotao.saltedfishcloud.model.websocket.WebSocketRequest;
import com.xiaotao.saltedfishcloud.model.websocket.WebSocketResponse;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/api/ws")
public class WebSocketHandler {
    @Autowired
    private AsyncTaskManager asyncTaskManager;
//    private final AsyncTaskManager asyncTaskManager = SpringContextUtils.getContext().getBean(AsyncTaskManager.class);


    /**
     * 已订阅的主题
     */
    private final Map<String, Long> subscribedTopic = new ConcurrentHashMap<>();

    private AsyncTaskManager getAsyncTaskManager() {
        if (asyncTaskManager == null) {
            asyncTaskManager = SpringContextUtils.getContext().getBean(AsyncTaskManager.class);
        }
        return asyncTaskManager;
    }

    /**
     * 收到客户端消息后调用的方法
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        WebSocketRequest request = MapperHolder.parseAsJson(message, WebSocketRequest.class);
        Long taskId = TypeUtils.toLong(request.getData());
        String topic = MQTopic.Prefix.ASYNC_TASK_EXIT + taskId;

        // 订阅异步任务日志动作
        if(WebSocketConstant.Action.SUBSCRIBE_TASK_LOG.equals(request.getAction())) {
            if(subscribedTopic.containsKey(topic)) {
                return;
            }
            RemoteEndpoint.Async remote = session.getAsyncRemote();
            long listenId = getAsyncTaskManager().listenLog(taskId, logMessage -> {
//                log.debug("[WebSocket]向用户{}的连接发送消息...", session.getUserPrincipal().getName());
                try {
                    remote.sendText(MapperHolder.toJson(WebSocketResponse.builder()
                            .id(taskId)
                            .type(WebSocketConstant.Type.ASYNC_TASK_LOG)
                            .data(logMessage)
                            .build()
                    ));
                } catch (JsonProcessingException e) {
                    log.error("[WebSocket]向用户{}的连接发送消息失败，消息:{}...", session.getUserPrincipal().getName(), logMessage, e);
                }
            });
            subscribedTopic.put(topic, listenId);
        } else if (WebSocketConstant.Action.UNSUBSCRIBE_TASK_LOG.equals(request.getAction())) {
            // 取消任务日志订阅
            if(!subscribedTopic.containsKey(topic)) {
                return;
            }
            Long id = subscribedTopic.remove(topic);
            asyncTaskManager.removeLogListen(id);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        log.debug("用户{}连接WebSocket", session.getUserPrincipal().getName());
    }


    @OnClose
    public void onClose(Session session) {
        log.debug("用户{}断开WebSocket连接", session.getUserPrincipal().getName());
        subscribedTopic.values().forEach(getAsyncTaskManager()::removeLogListen);
    }
}
