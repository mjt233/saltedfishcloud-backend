package com.xiaotao.saltedfishcloud.service.breakpoint.merge;

import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;

import java.io.IOException;

public class MergeBreakpointFileProviderImpl implements MergeBreakpointFileProvider {
    private final TaskManager manager;

    public MergeBreakpointFileProviderImpl(TaskManager manager) {
        this.manager = manager;
    }

    @Override
    public MergeBreakpointMultipartFile getFile(String taskId) throws IOException {
        TaskMetadata task = manager.queryTask(taskId);
        return new MergeBreakpointMultipartFile(task, manager);
    }
}
