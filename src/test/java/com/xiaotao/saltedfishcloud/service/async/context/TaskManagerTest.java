package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.TestTask;
import com.xiaotao.saltedfishcloud.service.async.TestTask2;
import com.xiaotao.saltedfishcloud.service.async.task.AbstractAsyncTask;
import lombok.var;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

class TaskManagerTest {

    @Test
    public void doTest() throws InterruptedException {

        TaskManagerImpl taskManager = new TaskManagerImpl();
        TaskContextFactory factory = new TaskContextFactory(taskManager);

        var t1 = factory.createContextFromAbstractAsyncTask(TestTask.class);
        var t2 = factory.createContextFromAbstractAsyncTask(TestTask2.class);
        taskManager.submit(t2);
        taskManager.submit(t1);
        Thread.sleep(100);
        System.out.println("任务1是否过期：" + t1.isExpire());
        System.out.println("任务2是否过期：" + t2.isExpire());

        System.out.println("中断前 - 任务1是否过期：" + t1.isExpire() + " 是否完成：" + t1.isFinish());
        t1.interrupt();
        Thread.sleep(1000);
        System.out.println("中断后 - 任务1是否过期：" + t1.isExpire());
        System.out.println("回收前 - 任务1是否被回收：" + (taskManager.getTask(t1.getId()) == null));
        taskManager.gc();
        System.out.println("回收后 - 任务1是否被回收：" + (taskManager.getTask(t1.getId()) == null));
    }
}
