package com.xiaotao.saltedfishcloud.service.async;

import com.xiaotao.saltedfishcloud.service.async.io.TaskMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AbstractAsyncTask;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTaskResult;

public class TestTask extends AbstractAsyncTask<String, String> {
    public TestTask(TaskMessageIOPair<String> input, TaskMessageIOPair<String> output) {
        super(input, output);
    }

    @Override
    protected AsyncTaskResult execute() {
        System.out.println("我被执行了");
        provideMessage("数据1");
        provideMessage("数据2");
        provideMessage("数据3");
        provideMessage("数据4");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return AsyncTaskResult.getInstance(AsyncTaskResult.Status.SUCCESS, 0);
    }

    @Override
    public String getStatus() {
        return "我被取了状态";
    }

    @Override
    public void interrupt() {
        System.out.println("收到中断");
        super.interrupt();
    }

    @Override
    protected void doInterrupt() {

    }
}
