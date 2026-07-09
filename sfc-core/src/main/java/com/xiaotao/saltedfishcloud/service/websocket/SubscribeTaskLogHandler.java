package com.xiaotao.saltedfishcloud.service.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.task.constants.TaskMQTopic;
import com.xiaotao.saltedfishcloud.constant.WebSocketConstant;
import com.xiaotao.saltedfishcloud.model.websocket.WebSocketRequest;
import com.xiaotao.saltedfishcloud.model.websocket.WebSocketResponse;
import com.xiaotao.saltedfishcloud.service.mq.MQTopic;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 处理订阅异步任务日志动作（{@link WebSocketConstant.Action#SUBSCRIBE_TASK_LOG}）。
 */
@Slf4j
@Component
public class SubscribeTaskLogHandler implements WebSocketActionHandler {
    private static final String LOG_PREFIX = "[WebSocket]";

    @Autowired
    private WebSocketSubscriptionManager subscriptionManager;

    @Override
    public String getAction() {
        return WebSocketConstant.Action.SUBSCRIBE_TASK_LOG;
    }

    @Override
    public void handle(WebSocketRequest request, Session session, WebSocketMessageSender sender) {
        Long taskId = TypeUtils.toLong(request.getData());
        MQTopic<String> mqTopic = TaskMQTopic.Get.ASYNC_TASK_LOG(taskId);
        String topic = mqTopic.getTopic();

        if (subscriptionManager.isSubscribed(topic)) {
            return;
        }

        subscriptionManager.subscribeTaskLog(topic, taskId, logMessage -> {
            try {
                String response = MapperHolder.toJson(WebSocketResponse.builder()
                        .id(taskId)
                        .type(WebSocketConstant.Type.ASYNC_TASK_LOG)
                        .data(logMessage)
                        .build()
                );
                sender.send(response);
            } catch (JsonProcessingException e) {
                log.error("{}向用户{}发送消息失败", LOG_PREFIX, session.getUserPrincipal(), e);
            }
        });
    }
}
