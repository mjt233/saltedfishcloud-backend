package com.sfc.task;

/**
 * 异步任务常量
 */
public interface AsyncTaskConstants {
    /**
     * 任务状态
     */
    interface Status {
        int WAITING = 0;
        int RUNNING = 1;
        int FINISH = 2;
        int FAILED = 3;
    }

    interface RedisKey {
        String TASK_QUEUE = "ASYNC_TASK_QUEUE";
    }
}
