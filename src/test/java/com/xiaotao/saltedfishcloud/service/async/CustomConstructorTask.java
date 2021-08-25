package com.xiaotao.saltedfishcloud.service.async;

import com.xiaotao.saltedfishcloud.service.async.io.impl.StringMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AbstractAsyncTask;

public class CustomConstructorTask extends AbstractAsyncTask<String, String> {
    public CustomConstructorTask() {
        super(new StringMessageIOPair(), new StringMessageIOPair());
    }

    @Override
    protected long execute() {
        System.out.println("任务已执行，3s后过期");
        return 3;
    }

    @Override
    public String getStatus() {
        return "啦啦啦";
    }
}
