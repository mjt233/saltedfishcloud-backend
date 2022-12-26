package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;
import lombok.Getter;

import java.util.UUID;

/**
 * 默认的任务上下文接口实现<br>
 * todo 使用线程池
 * todo 抽离该部分逻辑到管理器中，该类多余，模糊了task和manager职责
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class TaskContextImpl<T> implements TaskContext<T> {
    private final AsyncTask task;
    private final Thread thread;
    private AsyncTackCallback success = EmptyCallback.get();
    private AsyncTackCallback failed = EmptyCallback.get();
    private AsyncTackCallback finish = EmptyCallback.get();
    private AsyncTackCallback startCallback = EmptyCallback.get();
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
            } finally {
                try {
                    this.finish.action();
                } catch( Throwable e) {
                    e.printStackTrace();
                }
            }
            if (r) {
                this.success.action();
            } else {
                this.failed.action();
            }
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
    public void onStart(AsyncTackCallback callback) {
        this.startCallback = callback;
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
    public synchronized void start() {
        if (!started) {
            started = true;
            startCallback.action();
            thread.start();
        }
    }
}
