package com.sfc.task;

/**
 * 异步任务常量
 */
public interface AsyncTaskConstants {
    /**
     * 任务状态
     */
    interface Status {
        Integer WAITING = 0;
        Integer RUNNING = 1;
        Integer FINISH = 2;
        Integer FAILED = 3;
        Integer CANCEL = 4;
    }

    interface RedisKey {
        String TASK_QUEUE = "ASYNC_TASK_QUEUE";
    }
}
