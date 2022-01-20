package com.xiaotao.saltedfishcloud.service.async.context;

public interface TaskContextFactory {
    TaskManager getManager();

    /**
     * 直接装配一个继承了AbstractAsyncTask的AsyncTask任务类并创建他的任务上下文
     *
     * @param t   AbstractAsyncTask的子类的Class
     * @param <T> AbstractAsyncTask的子类类型
     * @return 一个包含了新的AbstractAsyncTask的子字类的TaskContext
     */
    <T> TaskContext<T> createContextFromAbstractAsyncTask(Class<T> t);

    /**
     * 从现有的AsyncTask实例创建任务上下文
     *
     * @param task AsyncTask任务实例
     * @param <T>  AsyncTask任务实例类型
     */
    <T> TaskContext<T> createContextFromAsyncTask(T task);
}
