package com.sfc.task;

/**
 * 异步任务的创建工厂，接受任务参数后需要能够创建可执行的任务实例。
 * 若实现了这个接口的类被注入到Spring Bean容器中，会被自动注册到异步任务工厂{@link AsyncTaskManager#registerFactory(AsyncTaskFactory)}
 */
public interface AsyncTaskFactory {
    /**
     * 根据JSON序列化参数创建一个异步任务
     * @param params    参数
     */
    AsyncTask createTask(String params);

    /**
     * 获取该工厂的任务类型
     */
    String getTaskType();
}
