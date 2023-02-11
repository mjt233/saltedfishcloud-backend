package com.saltedfishcloud.ext.ve.task;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VideoConvertTaskFactory implements AsyncTaskFactory {
    @Autowired
    private FFMpegHelper ffMpegHelper;
    @Autowired
    private ResourceService resourceService;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        return new VideoConvertTask(params, resourceService, ffMpegHelper);
    }

    @Override
    public String getTaskType() {
        return VEConstants.TASK_TYPE;
    }
}
