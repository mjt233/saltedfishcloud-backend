package com.xiaotao.saltedfishcloud.service.async;

import com.xiaotao.saltedfishcloud.service.async.io.TaskMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AbstractAsyncTask;

public class TestTask2 extends AbstractAsyncTask<String, String> {
    public TestTask2(TaskMessageIOPair<String> input, TaskMessageIOPair<String> output) {
        super(input, output);
    }

    @Override
    protected long execute() {
        System.out.println("我是任务2，我被执行了");
        return 0;
    }



    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public synchronized void interrupt() {
        super.interrupt();
    }
}
