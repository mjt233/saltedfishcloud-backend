package com.sfc.task;

import com.sfc.task.model.AsyncTaskProgress;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

@Component
public class TestAsyncTaskFactory implements AsyncTaskFactory {
    @Override
    public AsyncTask createTask(String params) {
        return new AsyncTask() {
            private Thread executeThread;

            private boolean finish = false;
            @Override
            public void execute(OutputStream logOutputStream) {
                try (OutputStreamWriter writer = new OutputStreamWriter(logOutputStream)) {
                    int i = Integer.parseInt(params);
                    writer.write("开始执行：" + System.currentTimeMillis() + "\n");
                    System.out.println("开始睡" + i + "ms");
                    try {
                        executeThread = Thread.currentThread();
                        Thread.sleep(i);
                        System.out.println("睡醒了");
                        writer.write("执行完成：" + System.currentTimeMillis() + "\n");
                    } catch (InterruptedException e) {
                        writer.write("执行被打断，执行完成：" + System.currentTimeMillis() + "\n");
                    }
                } catch (IOException ignore) {
                } finally {
                    finish = true;
                }

            }

            @Override
            public void interrupt() {
                if(executeThread != null) {
                    executeThread.interrupt();
                }
            }

            @Override
            public boolean isRunning() {
                return !finish;
            }

            @Override
            public String getParams() {
                return params;
            }

            @Override
            public AsyncTaskProgress getProgress() {
                return null;
            }
        };
    }

    @Override
    public String getTaskType() {
        return "test";
    }
}
