package com.xiaotao.saltedfishcloud.download.task;

import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.download.repo.DownloadTaskRepo;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DownloadAsyncTaskFactory implements AsyncTaskFactory {
    @Autowired
    private ProxyInfoService proxyInfoService;

    @Autowired
    private DownloadTaskRepo downloadTaskRepo;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private NodeService nodeService;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        DownloadAsyncTask task = new DownloadAsyncTask(params);
        task.setProxyInfoService(proxyInfoService);
        task.setDownloadTaskRepo(downloadTaskRepo);
        task.setDiskFileSystem(diskFileSystemManager.getMainFileSystem());
        task.setNodeService(nodeService);
        return task;
    }

    @Override
    public String getTaskType() {
        return AsyncTaskType.OFFLINE_DOWNLOAD;
    }
}
