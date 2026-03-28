package com.saltedfishcloud.ext.ve.task;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoConvertTaskFactory implements AsyncTaskFactory {
    private final FFMpegHelper ffMpegHelper;
    private final ResourceService resourceService;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        return new VideoConvertTask(params, resourceService, ffMpegHelper, asyncTaskRecord);
    }

    @Override
    public String getTaskType() {
        return VEConstants.TASK_TYPE;
    }
}
