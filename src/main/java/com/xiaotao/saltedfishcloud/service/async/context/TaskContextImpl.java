package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;
import lombok.Getter;

import java.util.UUID;
class DefaultCallback implements AsyncTackCallback {
    private DefaultCallback() {}
    public final static DefaultCallback instance = new DefaultCallback();

    @Override
    public void action() {

    }
}
/**
 * 默认的任务上下文接口实现
 * @TODO 使用线程池
 */
public class TaskContextImpl<T> implements TaskContext<T> {
    private final AsyncTask task;
    private final Thread thread;
    private AsyncTackCallback success = DefaultCallback.instance;
    private AsyncTackCallback failed = DefaultCallback.instance;
    private AsyncTackCallback finish = DefaultCallback.instance;
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
            boolean r;
            try {
                r = this.task.start();
            } catch (Throwable e) {
                e.printStackTrace();
                this.failed.action();
                manager.remove(id);
                return;
            }
            if (this.task.isExpire()) {
                manager.remove(id);
            }
            if (r) {
                this.success.action();
            } else {
                this.failed.action();
            }
        });
    }

    @Override
    public boolean isExpire() {
        return task.isExpire();
    }


    @Override
    public void onSuccess(AsyncTackCallback callback) {
        this.success = callback;
    }

    @Override
    public void onFailed(AsyncTackCallback callback) {
        this.failed = callback;
    }

    @Override
    public void onFinish(AsyncTackCallback callback) {
        this.finish = callback;
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
