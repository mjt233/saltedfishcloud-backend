package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;

public interface TaskManager {
    void submit(TaskContext<? extends AsyncTask> task);

    /**
     * 移除指定的任务
     * @param task 任务上下文
     * @return 移除成功为true，否则为false（无效的任务ID时为false）
     */
    default boolean remove(TaskContext<? extends AsyncTask> task) {
        return remove(task.getId());
    }

    /**
     * 移除指定的任务
     * @param id 任务ID
     * @return 移除成功为true，否则为false（无效的任务ID时为false）
     */
    boolean remove(String id);

    TaskContext<? extends AsyncTask> getContext(String taskId);

    <T> TaskContext<T> getContext(String taskId, Class<T> type);

    /**
     * 移除所有失效的任务
     */
    void gc();
}
