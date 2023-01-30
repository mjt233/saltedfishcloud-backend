package com.sfc.job.test;

import com.sfc.job.AsyncTask;
import com.sfc.job.AsyncTaskExecutor;
import com.sfc.job.AsyncTaskProgress;
import com.sfc.job.AsyncTaskRecord;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.function.Supplier;

public class TaskExecuteTest {
    @Test
    public void test() throws InterruptedException {
        AsyncTaskExecutor executor = new AsyncTaskExecutor(getTaskRecordList(), new HashMap<>() {{
            put("test", params -> new AsyncTask() {
                @Override
                public void execute(OutputStream logOutputStream) {
                    System.out.println(params);
                }

                @Override
                public void interrupt() {

                }

                @Override
                public boolean isRunning() {
                    return false;
                }

                @Override
                public String getParams() {
                    return params;
                }

                @Override
                public AsyncTaskProgress getProgress() {
                    return null;
                }
            });
        }});

        executor.start();
        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            System.out.println("测试完成");
        }


    }

    public Supplier<AsyncTaskRecord> getTaskRecordList() {
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
        return () -> {
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
        };
    }
}
