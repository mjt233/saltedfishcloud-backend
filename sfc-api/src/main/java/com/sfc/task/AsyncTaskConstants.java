package com.sfc.task;

/**
 * 异步任务常量
 */
public interface AsyncTaskConstants {
    /**
     * 任务状态
     */
    interface Status {
        /**
         * 在队列中等待
         */
        Integer WAITING = 0;

        /**
         * 运行中
         */
        Integer RUNNING = 1;

        /**
         * 任务完成
         */
        Integer FINISH = 2;

        /**
         * 任务失败
         */
        Integer FAILED = 3;

        /**
         * 任务被取消/中断
         */
        Integer CANCEL = 4;

        /**
         * 任务离线（负责执行的节点掉线）
         */
        Integer OFFLINE = 5;
    }

    interface RedisKey {
        String TASK_QUEUE = "ASYNC_TASK_QUEUE";
    }
}
