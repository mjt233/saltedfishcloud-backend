package com.sfc.job;


import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 异步任务执行器
 */
@Slf4j
public class AsyncTaskExecutor {
    /**
     * 系统最大负载
     */
    @Getter
    @Setter
    private int maxLoad = Runtime.getRuntime().availableProcessors() * 100 - 10;

    private final AtomicInteger currentLoad = new AtomicInteger(0);

    @Getter
    private final Supplier<AsyncTaskRecord> taskReceiver;

    @Getter
    private final Map<String, AsyncTaskFactory> taskFactories;

    private final Map<Long, AsyncTaskRecord> runningTask = new ConcurrentHashMap<>();

    // 负载清算队列，当异步任务完成时
    private final BlockingQueue<Integer> loadCleanQueue = new LinkedBlockingQueue<>();

    private boolean started = false;

    private int threadCount = 0;
    private final Executor threadPool = new ThreadPoolExecutor(
            1,
            1024,
            10,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1),
            r -> {
                String name;
                if (threadCount == 0) {
                    name = "AsyncTaskReceiver-" + this.hashCode() % 10000;
                } else {
                    name = "AsyncTaskExecutor-" + this.hashCode() % 10000 + "-" + threadCount++;
                }
                Thread thread = new Thread(r);
                thread.setName(name);
                return thread;
            }
    );

    /**
     * 任务接收锁，当负载不再存在余量时无法被任务接受线程获取锁从而阻塞任务接收
     */
    private final Lock lock = new ReentrantLock();

    public int getCurrentLoad() {
        return currentLoad.get();
    }

    /**
     * @param taskReceiver      任务接收函数（接收下发到的任务）
     * @param taskFactories     可执行任务创建函数,key为任务类型
     */
    public AsyncTaskExecutor(Supplier<AsyncTaskRecord> taskReceiver, Map<String, AsyncTaskFactory> taskFactories) {
        this.taskReceiver = taskReceiver;
        this.taskFactories = taskFactories;
    }

    /**
     * 开始接受任务
     */
    public synchronized void start() {
        if (started) {
            throw new IllegalArgumentException("已经启动了");
        }
        threadPool.execute(() -> {
            while (true) {
                try {
                    // 等待负载值降低到最大限度以下
                    waitLoad();
                } catch (InterruptedException e) {
                    log.error("负载判断等待被中断", e);
                    continue;
                }

                // 接受一个任务
                TaskContext taskContext = receiveTask();
                AsyncTask asyncTask = taskContext.task;
                AsyncTaskRecord record = taskContext.record;

                // 更新负载记录值
                int cpuOverhead = Optional.ofNullable(record.getCpuOverhead()).orElse(0);
                if (cpuOverhead > 0) {
                    currentLoad.addAndGet(cpuOverhead);
                }

                threadPool.execute(() -> {
                    Path logPath = PathUtils.getLogDirectory().resolve("async_task_" + record.getId() + ".log");
                    try (OutputStream logOutput = Files.newOutputStream(logPath)) {
                        asyncTask.execute(logOutput);
                    } catch (Throwable e) {
                        log.error("异步任务{}执行出错", record.getId(), e);
                    } finally {
                        // 任务完成，添加负载值到待清算的队列
                        if (cpuOverhead > 0) {
                            loadCleanQueue.add(cpuOverhead);
                        }
                    }
                });

            }
        });
    }

    /**
     * 等待负载值降低到最大值以下
     */
    private void waitLoad() throws InterruptedException {
        // 检查是否过载，若已过载，则进入负载清算程序，将已完成的负载值从当前负载中移除
        if (!testLoad()) {
            log.debug("负载清算，当前负载: {}", currentLoad.get());
            // 第一次清算，直到没有可清算的负载导致阻塞 或 负载值降低到最大值以下
            do {
                Integer take = loadCleanQueue.take();
                currentLoad.addAndGet(take * -1);
                log.debug("负载降低: {}，当前负载: {} 最大负载: {}", take, currentLoad.get(), maxLoad);
            } while (!testLoad());

            // 再把剩余可清算的负载也清算了，
            while (!loadCleanQueue.isEmpty()) {
                Integer take = loadCleanQueue.take();
                currentLoad.addAndGet(take * -1);
            }
            log.debug("清算完成，当前负载: {}", currentLoad.get());
        }
    }

    /**
     * 测试是否满载过超载
     */
    private boolean testLoad() {
        return currentLoad.get() <= maxLoad;
    }

    protected TaskContext receiveTask() {
        AsyncTaskRecord record = taskReceiver.get();
        AsyncTaskFactory asyncTaskFactory = taskFactories.get(record.getTaskType());

        if (asyncTaskFactory == null) {
            throw new IllegalArgumentException("找不到任务类型为 " + record.getTaskType() + " 的任务工厂");
        }

        AsyncTask task = asyncTaskFactory.createTask(record.getParams());
        return new TaskContext(task, record);

    }

    @AllArgsConstructor
    private static class TaskContext {
        public AsyncTask task;
        public AsyncTaskRecord record;
    }
}
