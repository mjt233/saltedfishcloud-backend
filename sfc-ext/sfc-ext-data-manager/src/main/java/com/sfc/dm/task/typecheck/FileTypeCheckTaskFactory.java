package com.sfc.dm.task.typecheck;

import com.sfc.dm.constant.DataManagerTaskType;
import com.sfc.dm.model.dto.IdentifyParam;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.dm.service.identify.FileTypeChecker;
import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

/**
 * 文件类型识别任务工厂
 */
@RequiredArgsConstructor
public class FileTypeCheckTaskFactory implements AsyncTaskFactory {
    private final InvalidDataRecordRepo invalidDataRepo;
    private final StoreServiceFactory storeServiceFactory;
    private final FileTypeChecker fileTypeChecker;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        FileTypeCheckTask task = new FileTypeCheckTask();
        task.setInvalidDataRepo(invalidDataRepo);
        task.setStoreServiceFactory(storeServiceFactory);
        task.setFileTypeChecker(fileTypeChecker);

        if (params != null && !params.isEmpty()) {
            try {
                IdentifyParam identifyParam = MapperHolder.parseJson(params, IdentifyParam.class);
                task.setTargetIds(identifyParam.getIds());
                task.setReIdentify(Boolean.TRUE.equals(identifyParam.getReIdentify()));
            } catch (IOException e) {
                throw new RuntimeException("解析识别参数失败", e);
            }
        }

        return task;
    }

    @Override
    public String getTaskType() {
        return DataManagerTaskType.FILE_TYPE_CHECK;
    }
}
