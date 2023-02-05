package com.xiaotao.saltedfishcloud.download.task;

import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DownloadAsyncTaskFactory implements AsyncTaskFactory {
    @Autowired
    private ProxyDao proxyDao;

    @Override
    public AsyncTask createTask(String params) {
        DownloadAsyncTask task = new DownloadAsyncTask(params);
        task.setProxyDao(proxyDao);
        return task;
    }

    @Override
    public String getTaskType() {
        return AsyncTaskType.OFFLINE_DOWNLOAD;
    }
}
