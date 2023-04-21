package com.sfc.archive.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.archive.model.ArchiveParam;
import com.sfc.constant.AsyncTaskType;
import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.springframework.stereotype.Component;

/**
 * 压缩文件任务工厂
 */
@Component
public class CompressAsyncTaskFactory implements AsyncTaskFactory {
    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        try {
            ArchiveParam archiveParam = MapperHolder.parseJson(params, ArchiveParam.class);
            return new CompressAsyncTask(archiveParam, params);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getTaskType() {
        return AsyncTaskType.ARCHIVE_COMPRESS;
    }
}
