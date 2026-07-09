package com.xiaotao.saltedfishcloud.service.websocket;

import com.sfc.task.constants.TaskMQTopic;
import com.xiaotao.saltedfishcloud.constant.WebSocketConstant;
import com.xiaotao.saltedfishcloud.model.websocket.WebSocketRequest;
import com.xiaotao.saltedfishcloud.service.mq.MQTopic;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import jakarta.websocket.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 处理取消订阅异步任务日志动作（{@link WebSocketConstant.Action#UNSUBSCRIBE_TASK_LOG}）。
 */
@Component
public class UnsubscribeTaskLogHandler implements WebSocketActionHandler {

    @Autowired
    private WebSocketSubscriptionManager subscriptionManager;

    @Override
    public String getAction() {
        return WebSocketConstant.Action.UNSUBSCRIBE_TASK_LOG;
    }

    @Override
    public void handle(WebSocketRequest request, Session session, WebSocketMessageSender sender) {
        Long taskId = TypeUtils.toLong(request.getData());
        MQTopic<String> mqTopic = TaskMQTopic.Get.ASYNC_TASK_LOG(taskId);
        String topic = mqTopic.getTopic();

        if (!subscriptionManager.isSubscribed(topic)) {
            return;
        }

        subscriptionManager.unsubscribeTaskLog(topic);
    }
}
