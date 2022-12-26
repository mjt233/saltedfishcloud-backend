package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EncodeConvertAsyncTaskFactory {
    @Autowired
    private FFMpegHelper ffMpegHelper;

    @Autowired
    private ResourceService resourceService;

    public EncodeConvertAsyncTask createTask(EncodeConvertTaskParam param) {
        EncodeConvertAsyncTask task = new EncodeConvertAsyncTask();
        this.paddingDependence(task);
        task.setParam(param);
        return task;
    }

    private void paddingDependence(EncodeConvertAsyncTask task) {
        task.setFfMpegHelper(ffMpegHelper);
    }
}
