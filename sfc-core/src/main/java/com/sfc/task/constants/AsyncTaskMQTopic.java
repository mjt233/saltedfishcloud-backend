package com.sfc.task.constants;

import com.xiaotao.saltedfishcloud.service.mq.MQTopic;

public interface AsyncTaskMQTopic {
    MQTopic<Long> TASK_PUBLISH = new MQTopic<>(() -> "async_task_publish") {};
}
