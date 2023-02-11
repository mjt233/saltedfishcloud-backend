package com.sfc.task;

import com.sfc.task.model.AsyncTaskRecord;

/**
 * 异步任务接收器，用于从集群中接受任务数据
 */
@FunctionalInterface
public interface AsyncTaskReceiver {
    /**
     * 获取一个任务，若返回null则表示接收停止。<br>
     * 接收停止会导致执行器接收线程退出，表示。
     */
    AsyncTaskRecord get();

    /**
     * 开始接收
     */
    default void start() {}

    /**
     * 停止接收
     */
    default void interrupt() {}
}
