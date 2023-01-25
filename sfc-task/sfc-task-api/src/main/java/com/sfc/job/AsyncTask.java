package com.sfc.job;

/**
 * 异步任务
 */
public interface AsyncTask {

    /**
     * 开始执行
     */
    void execute();

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
