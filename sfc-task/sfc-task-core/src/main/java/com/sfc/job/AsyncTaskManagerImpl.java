package com.sfc.job;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AsyncTaskManagerImpl implements AsyncTaskManager {

    private final Map<String, AsyncTaskFactory> factoryMap = new HashMap<>();

    public AsyncTaskManagerImpl() {
    }

    @Override
    public void registerFactory(String type, AsyncTaskFactory factory) {
        if (factoryMap.containsKey(type)) {
            throw new IllegalArgumentException("任务类型 " + type + " 的任务工厂已被注册");
        }
        factoryMap.put(type, factory);
    }

    @Override
    public void submitAsyncTask(String type, AsyncTaskRecord record) {
        AsyncTaskFactory factory = factoryMap.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("找不到类型 " + type + "的任务工厂");
        }
    }

    @Override
    public InputStream getTaskLog(Long taskId, boolean withHistory) {

        return null;
    }
}
