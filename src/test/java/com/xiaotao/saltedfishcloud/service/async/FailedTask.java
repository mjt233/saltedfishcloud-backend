package com.xiaotao.saltedfishcloud.service.async;

import com.xiaotao.saltedfishcloud.service.async.io.TaskMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AbstractAsyncTask;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTaskResult;

public class FailedTask extends AbstractAsyncTask<String, String> {
    public FailedTask(TaskMessageIOPair<String> input, TaskMessageIOPair<String> output) {
        super(input, output);
    }

    @Override
    protected AsyncTaskResult execute() {
        return AsyncTaskResult.getInstance(AsyncTaskResult.Status.FAILED, 1);
    }

    @Override
    public String getStatus() {
        return null;
    }
}
