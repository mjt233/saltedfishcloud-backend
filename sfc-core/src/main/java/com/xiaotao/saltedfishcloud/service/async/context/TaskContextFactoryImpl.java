package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.TaskConstructException;
import com.xiaotao.saltedfishcloud.service.async.io.TaskMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.io.impl.StringMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class TaskContextFactoryImpl implements TaskContextFactory {
    private final TaskManager taskManager;
    public TaskContextFactoryImpl(TaskManager manager) {
        this.taskManager = manager;
    }

    @Override
    public TaskManager getManager() {
        return taskManager;
    }

    @Override
    public <T> TaskContext<T> createContextFromAbstractAsyncTask(Class<T> t) {
        Constructor<T> constructor;
        try {
            constructor = t.getConstructor(TaskMessageIOPair.class, TaskMessageIOPair.class);
            T task = constructor.newInstance(new StringMessageIOPair(), new StringMessageIOPair());
            return new TaskContextImpl<>(task, taskManager);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(t.getName() + "is not an AbstractAsyncTask class");
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new TaskConstructException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public <T> TaskContext<T> createContextFromAsyncTask(T task) {
        if ( !(task instanceof AsyncTask) ) {
            throw new IllegalArgumentException(task.getClass().getName() + " is not an AsyncTask");
        }
        return new TaskContextImpl<>(task, taskManager);
    }
}
