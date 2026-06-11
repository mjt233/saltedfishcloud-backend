package com.sfc.dm.task;

import com.sfc.dm.constant.DataManagerTaskType;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 失效数据检测任务工厂
 */
@Component
@RequiredArgsConstructor
public class InvalidDataDetectTaskFactory implements AsyncTaskFactory {
    private final InvalidDataRecordRepo invalidDataRepo;
    private final StoreServiceFactory storeServiceFactory;
    private final DiskFileSystemManager fileSystemManager;
    private final FileInfoRepo fileInfoRepo;
    private final SysCommonConfig sysCommonConfig;
    private final UserService userService;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        InvalidDataDetectTask task = new InvalidDataDetectTask();
        task.setInvalidDataRepo(invalidDataRepo);
        task.setStoreServiceFactory(storeServiceFactory);
        task.setFileSystemManager(fileSystemManager);
        task.setFileInfoRepo(fileInfoRepo);
        task.setSysCommonConfig(sysCommonConfig);
        task.setUserService(userService);
        return task;
    }

    @Override
    public String getTaskType() {
        return DataManagerTaskType.INVALID_DATA_DETECT;
    }
}
