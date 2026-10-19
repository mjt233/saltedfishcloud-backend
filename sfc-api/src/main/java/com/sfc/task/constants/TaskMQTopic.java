package com.sfc.task.constants;

import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.service.mq.MQTopic;

public interface TaskMQTopic {

    /**
     * 前缀类型的消息主题
     */
    interface Prefix {

        /**
         * 异步任务执行退出消息
         */
        String ASYNC_TASK_EXIT = "async_task_exit/";

        /**
         * 异步任务日志更新
         */
        String ASYNC_TASK_LOG = "async_task_log/";
    }

    interface Get {
        /**
         * 异步任务日志更新
         * @param taskId 任务id
         */
        static MQTopic<String> ASYNC_TASK_LOG(Long taskId) {
            return new MQTopic<>(() -> Prefix.ASYNC_TASK_LOG + taskId) {};
        }

        /**
         * 异步任务执行退出消息
         * @param taskId 任务id
         */
        static MQTopic<AsyncTaskRecord> ASYNC_TASK_EXIT(Long taskId) {
            return new MQTopic<>(() -> Prefix.ASYNC_TASK_EXIT + taskId) {};
        }
    }
}
