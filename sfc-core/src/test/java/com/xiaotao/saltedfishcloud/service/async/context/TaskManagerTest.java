package com.xiaotao.saltedfishcloud.service.async.context;

import com.xiaotao.saltedfishcloud.service.async.CustomConstructorTask;
import com.xiaotao.saltedfishcloud.service.async.FailedTask;
import com.xiaotao.saltedfishcloud.service.async.TestTask;
import com.xiaotao.saltedfishcloud.service.async.TestTask2;
import org.junit.jupiter.api.Test;

class TaskManagerTest {

    private final TaskManagerImpl taskManager = new TaskManagerImpl();
    private final TaskContextFactory factory = new TaskContextFactoryImpl(taskManager);

    @Test
    public void testCallback() {
        TaskContext<TestTask> context = factory.createContextFromAbstractAsyncTask(TestTask.class);
        context.onSuccess(() -> {
            System.out.println("success");
        });
        context.onFinish(() -> {
            System.out.println("finish");
        });
        taskManager.submit(context);
        TaskContext<FailedTask> context2 = factory.createContextFromAbstractAsyncTask(FailedTask.class);
        context2.onFinish(() -> {
            System.out.println("");
        });
    }

    @Test
    public void doTest() throws InterruptedException {

        TaskContext
                <com.xiaotao.saltedfishcloud.service.async.TestTask> t1 = factory.createContextFromAbstractAsyncTask(TestTask.class);
        TaskContext
                <com.xiaotao.saltedfishcloud.service.async.TestTask2> t2 = factory.createContextFromAbstractAsyncTask(TestTask2.class);
        taskManager.submit(t2);
        taskManager.submit(t1);
        Thread.sleep(100);
        System.out.println("任务1是否过期：" + t1.isExpire());
        System.out.println("任务2是否过期：" + t2.isExpire());

        System.out.println("中断前 - 任务1是否过期：" + t1.isExpire() + " 是否完成：" + t1.isFinish());
        t1.interrupt();
        Thread.sleep(1000);
        System.out.println("中断后 - 任务1是否过期：" + t1.isExpire());
        System.out.println("回收前 - 任务1是否被回收：" + (taskManager.getContext(t1.getId()) == null));
        taskManager.gc();
        System.out.println("回收后 - 任务1是否被回收：" + (taskManager.getContext(t1.getId()) == null));
    }

    @Test
    public void testDelayExpire() throws InterruptedException {
        TaskContext<CustomConstructorTask> task = factory.createContextFromAsyncTask(new CustomConstructorTask());
        TaskContext<CustomConstructorTask> task2 = factory.createContextFromAsyncTask(new CustomConstructorTask());
        taskManager.submit(task);
        Thread.sleep(2000);
        taskManager.gc();
        System.out.println("2s过去，任务是否已过期：" + task.isExpire() + " 受管中：" + (taskManager.getContext(task.getId()) != null));
        Thread.sleep(2000);
        taskManager.gc();
        System.out.println("4s过去，任务是否已过期：" + task.isExpire() + " 受管中：" + (taskManager.getContext(task.getId()) != null));

        taskManager.submit(task2);
        Thread.sleep(100);
        System.out.println("0.1s过去，任务是否已过期：" + task2.isExpire() + " 是否已完成：" + task2.isFinish());
        task2.interrupt();
        Thread.sleep(100);
        System.out.println("发送中断，任务是否已过期：" + task2.isExpire() + " 是否已完成：" + task2.isFinish());

    }
}
