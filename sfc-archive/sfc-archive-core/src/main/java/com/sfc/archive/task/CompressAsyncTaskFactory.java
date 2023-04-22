package com.sfc.archive.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.archive.ArchiveManager;
import com.sfc.archive.model.ArchiveParam;
import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.sfc.constant.AsyncTaskType;
import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 压缩文件任务工厂
 */
@Component
public class CompressAsyncTaskFactory implements AsyncTaskFactory {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private ArchiveManager archiveManager;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        try {
            DiskFileSystemCompressParam compressParam = MapperHolder.parseJson(params, DiskFileSystemCompressParam.class);
            CompressAsyncTask asyncTask = new CompressAsyncTask(compressParam, params);
            asyncTask.setArchiveManager(archiveManager);
            asyncTask.setFileSystem(diskFileSystemManager.getMainFileSystem());
            return asyncTask;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getTaskType() {
        return AsyncTaskType.ARCHIVE_COMPRESS;
    }
}
