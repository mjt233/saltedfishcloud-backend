package com.sfc.task.test;

import com.sfc.task.*;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TaskExecuteTest {
    @Test
    public void test() throws InterruptedException {
        AsyncTaskExecutor executor = new DefaultAsyncTaskExecutor(getTaskRecordList());
        executor.registerFactory(new AsyncTaskFactory() {
            @Override
            public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
                return new AsyncTask() {
                    @Override
                    public void execute(OutputStream logOutputStream) {
                        System.out.println(params);
                    }
                    @Override
                    public void interrupt() { }

                    @Override
                    public boolean isRunning() {
                        return false;
                    }

                    @Override
                    public String getParams() {
                        return params;
                    }

                    @Override
                    public ProgressRecord getProgress() {
                        return null;
                    }
                };
            }

            @Override
            public String getTaskType() {
                return "test";
            }
        });
        executor.addTaskStartListener(r -> {
            System.out.println("任务[" + r.getName() + "]开始执行了");
        });

        executor.start();
        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            System.out.println("测试完成");
        }


    }

    public AsyncTaskReceiver getTaskRecordList() {
        Thread mainThread = Thread.currentThread();
        LinkedList<AsyncTaskRecord> result = new LinkedList<>();
        int taskCount = Runtime.getRuntime().availableProcessors() * 2;
        for (int i = 0; i < taskCount; i++) {
            result.add(AsyncTaskRecord.builder()
                            .cpuOverhead(100)
                            .taskType("test")
                            .params(i + "")
                            .name("第" + i + "个test任务")
                    .build());
        }
        return new AsyncTaskReceiver() {
            @Override
            public AsyncTaskRecord get() {
                if (!result.isEmpty()) {
                    return result.pop();
                } else {
                    try {
                        Thread.sleep(3000);
                        mainThread.interrupt();
                        Thread.sleep(3000);
                        return null;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            }

            @Override
            public List<AsyncTaskRecord> listQueue() {
                return Collections.emptyList();
            }
        };
    }
}
