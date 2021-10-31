package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public class TaskManagerImpl implements TaskManager {

    private final Map<String, TaskContext<? extends AsyncTask>> tasks = new HashMap<>();

    public synchronized void submit(TaskContext<? extends AsyncTask> task) {
        tasks.put(task.getId(), task);
        task.start();
    }

    @Override
    public synchronized boolean remove(String id) {
        log.debug("移除了任务{}", id);
        return tasks.remove(id) != null;
    }

    @Override
    public synchronized TaskContext<? extends AsyncTask> getContext(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public synchronized <T> TaskContext<T> getContext(String taskId, Class<T> type) {
        var task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        return (TaskContext<T>)task;
    }


    @Override
    public synchronized void gc() {
        tasks.entrySet().removeIf(stringTaskContextEntry -> stringTaskContextEntry.getValue().isExpire());
    }
}
