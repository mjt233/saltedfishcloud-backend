package com.sfc.constant;

public interface WebSocketConstant {
    interface Action {
        /**
         * 订阅消息
         */
        String SUBSCRIBE_TASK_LOG = "subscribe";

        /**
         * 取消订阅消息
         */
        String UNSUBSCRIBE_TASK_LOG = "unsubscribe";
    }

    interface Type {
        String ASYNC_TASK_LOG = "async_task_log";
    }
}
