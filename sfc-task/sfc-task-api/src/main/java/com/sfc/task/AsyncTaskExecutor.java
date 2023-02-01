package com.sfc.task;

import com.sfc.task.model.AsyncTaskRecord;

import java.util.function.Consumer;

public interface AsyncTaskExecutor {
    /**
     * 获取系统当前负载
     */
    int getCurrentLoad();

    /**
     * 开始接受任务
     */
    void start();

    /**
     * 停止全部任务并停止接受任务
     */
    void stop();

    /**
     * 是否处于运行中
     */
    boolean isRunning();

    /**
     * 获取系统最大负载
     */
    int getMaxLoad();

    /**
     * 中断任务
     * @param taskId 任务id
     */
    boolean interrupt(Long taskId);

    /**
     * 设置系统最大负载
     */
    void setMaxLoad(int maxLoad);

    /**
     * 注册一个任务工厂
     * @param factory 该类型对应的任务工厂
     */
    void registerFactory(AsyncTaskFactory factory);

    /**
     * 添加任务开始执行的监听器
     */
    void addTaskStartListener(Consumer<AsyncTaskRecord> listener);

    /**
     * 添加任务执行失败的监听器
     */
    void addTaskFailedListener(Consumer<AsyncTaskRecord> listener);

    /**
     * 添加任务执行完成的监听器
     */
    void addTaskFinishListener(Consumer<AsyncTaskRecord> listener);
}
