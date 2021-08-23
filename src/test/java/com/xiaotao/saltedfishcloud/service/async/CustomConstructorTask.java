package com.xiaotao.saltedfishcloud.service.async;

import com.xiaotao.saltedfishcloud.service.async.io.impl.StringMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AbstractAsyncTask;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTaskResult;

public class CustomConstructorTask extends AbstractAsyncTask<String, String> {
    public CustomConstructorTask() {
        super(new StringMessageIOPair(), new StringMessageIOPair());
    }

    @Override
    protected AsyncTaskResult execute() {

        return null;
    }

    @Override
    public String getStatus() {
        return "啦啦啦";
    }
}
