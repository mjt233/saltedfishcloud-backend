package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.TaskConstructException;
import com.xiaotao.saltedfishcloud.service.async.io.TaskMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.io.impl.StringMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;
import lombok.var;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class TaskContextFactory {
    private final TaskManager taskManager;
    public TaskContextFactory(TaskManager manager) {
        this.taskManager = manager;
    }

    public TaskManager getManager() {
        return taskManager;
    }

    /**
     * 直接装配一个继承了AbstractAsyncTask的AsyncTask任务类并创建他的任务上下文
     * @param t AbstractAsyncTask的子类的Class
     * @param <T> AbstractAsyncTask的子类类型
     * @return 一个包含了新的AbstractAsyncTask的子字类的TaskContext
     */
    public <T> TaskContext<T> createContextFromAbstractAsyncTask(Class<T> t) {
        Constructor<T> constructor;
        try {
            constructor = t.getConstructor(TaskMessageIOPair.class, TaskMessageIOPair.class);
            var task = constructor.newInstance(new StringMessageIOPair(), new StringMessageIOPair());
            return new TaskContextImpl<>(task, taskManager);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(t.getName() + "is not an AbstractAsyncTask class");
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new TaskConstructException(e.getMessage(), e.getCause());
        }
    }

    /**
     * 从现有的AsyncTask实例创建任务上下文
     * @param task  AsyncTask任务实例
     * @param <T>   AsyncTask任务实例类型
     */
    public <T> TaskContext<T> createContextFromAsyncTask(T task) {
        if ( !(task instanceof AsyncTask) ) {
            throw new IllegalArgumentException(task.getClass().getName() + " is not an AsyncTask");
        }
        return new TaskContextImpl<>(task, taskManager);
    }
}
