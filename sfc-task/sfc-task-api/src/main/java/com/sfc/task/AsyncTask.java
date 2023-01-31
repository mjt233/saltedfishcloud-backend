package com.sfc.task;

import com.sfc.task.model.AsyncTaskProgress;

import java.io.OutputStream;

/**
 * 异步任务
 */
public interface AsyncTask {

    /**
     * 开始执行任务。
     * @param logOutputStream 本次执行时可用于日志采集的输出流
     */
    void execute(OutputStream logOutputStream);

    /**
     * 发起任务中断执行请求
     */
    void interrupt();

    /**
     * 任务是否运行中
     */
    boolean isRunning();

    /**
     * 获取任务的执行参数（JSON序列化字符串）
     */
    String getParams();

    /**
     * 获取任务执行进度
     */
    AsyncTaskProgress getProgress();
}
