package com.sfc.task;

import com.sfc.task.model.AsyncTaskRecord;

import java.util.List;


public interface AsyncTaskReceiver {
    /**
     * 获取一个任务，若返回null则表示接收停止。<br>
     * 接收停止会导致执行器接收线程退出，表示。
     */
    AsyncTaskRecord get();

    /**
     * 获取当前在队列中等待中的任务
     */
    List<AsyncTaskRecord> listQueue();

    /**
     * 开始接收
     */
    default void start() {}

    /**
     * 停止接收
     */
    default void interrupt() {}
}
