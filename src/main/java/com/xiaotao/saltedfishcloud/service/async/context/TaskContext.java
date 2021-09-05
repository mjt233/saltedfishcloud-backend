package com.xiaotao.saltedfishcloud.service.async.context;

public interface TaskContext<T> {
    String getId();

    /**
     * 设置任务成功时的事件回调
     */
    void onSuccess(AsyncTackCallback callback);

    /**
     * 设置任务失败时的事件回调
     */
    void onFailed(AsyncTackCallback callback);

    /**
     * 设置任务完成时的事件回调，无论任务成功还是失败都会执行
     */
    void onFinish(AsyncTackCallback callback);


    /**
     * 任务执行是否已完成
     * @return 任务执行流程完成为true，否则为false
     */
    boolean isFinish();


    /**
     * 向任务发送中断信号，任务是否被中断取决由任务本身决定，该方法是异步执行的，不会等待任务
     */
    void interrupt();

    /**
     * 启动任务执行
     */
    void start();

    /**
     * 该上下文或任务是否过期，已过期时将会被管理器自动回收。注意：当任务执行完毕时候（isFinish()为true），若想仍然保留运行期间的消息数据一段时间，isExpire()应为false
     * @return true or false
     */
    boolean isExpire();

    T getTask();

    TaskManager getManager();
}
