package com.xiaotao.saltedfishcloud.task;

import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 文件复制异步任务工厂
 */
@Slf4j
@Component
public class FileCopyAsyncTaskFactory implements AsyncTaskFactory {

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        try {
            // 解析任务参数
            SimpleFileTransferParam param = MapperHolder.parseJson(params, SimpleFileTransferParam.class);

            // 创建任务实例
            FileCopyAsyncTask task = new FileCopyAsyncTask(params, param);

            // 注入文件系统依赖
            task.setDiskFileSystem(diskFileSystemManager.getMainFileSystem());

            log.debug("创建文件复制异步任务成功, 任务ID: {}", asyncTaskRecord.getId());
            return task;
        } catch (IOException e) {
            log.error("解析文件复制任务参数失败", e);
            throw new RuntimeException("解析任务参数失败", e);
        }
    }

    @Override
    public String getTaskType() {
        // 返回任务类型标识，与 AsyncTaskType.FILE_COPY 保持一致
        return AsyncTaskType.FILE_COPY;
    }
}