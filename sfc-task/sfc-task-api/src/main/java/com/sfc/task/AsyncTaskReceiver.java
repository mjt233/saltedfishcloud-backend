package com.sfc.task;

import com.sfc.task.model.AsyncTaskRecord;

import java.util.List;


public interface AsyncTaskReceiver {
    /**
     * 获取一个任务，当没有任务可获取时，该方法需要一直阻塞，直到有任务可获取。<br>
     * 当该方法成功返回一个任务后，表明该任务已被消费。<br>
     * 当该方法返回null则表示获取任务停止了。
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
