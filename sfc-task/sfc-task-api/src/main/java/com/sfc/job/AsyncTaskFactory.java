package com.sfc.job;

/**
 * 异步任务的创建工厂，接受任务参数后需要能够创建可执行的任务实例。
 */
public interface AsyncTaskFactory {
    /**
     * 根据JSON序列化参数创建一个异步任务
     * @param params    参数
     */
    AsyncTask createTask(String params);
}
