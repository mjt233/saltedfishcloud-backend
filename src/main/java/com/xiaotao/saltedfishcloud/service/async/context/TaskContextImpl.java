package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;
import lombok.Getter;

import java.util.UUID;
class DefaultCallback implements AsyncTaskEventCallback {
    private DefaultCallback() {}
    public final static DefaultCallback instance = new DefaultCallback();

    @Override
    public void onFinish() {

    }
}
/**
 * 默认的任务上下文接口实现
 * @TODO 使用线程池
 */
public class TaskContextImpl<T> implements TaskContext<T> {
    private final AsyncTask task;
    private final Thread thread;
    private AsyncTaskEventCallback callback;
    @Getter
    private final String id = UUID.randomUUID().toString();

    @Override
    public T getTask() {
        return (T)task;
    }

    private boolean started = false;

    @Override
    public TaskManager getManager() {
        return null;
    }


    public TaskContextImpl(T task, TaskManager manager) {
        if ( !(task instanceof AsyncTask)) {
            throw new IllegalArgumentException(task.getClass().getName() + " 不是一个有效的 AsyncTask");
        }
        this.task = (AsyncTask) task;
        this.thread = new Thread(() -> {
            this.task.start();
            this.callback.onFinish();
            if (this.task.isExpire()) {
                manager.remove(id);
            }
        });
    }

    @Override
    public boolean isExpire() {
        return task.isExpire();
    }


    @Override
    public void setCallback(AsyncTaskEventCallback callback) {
        this.callback = callback;
    }

    @Override
    public boolean isFinish() {
        return task.isFinish();
    }

    @Override
    public void interrupt() {
        new Thread(task::interrupt).start();
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
            thread.start();
        }
    }
}
