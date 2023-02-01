package com.sfc.task;


import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 异步任务执行器
 */
@Slf4j
@Component
public class DefaultAsyncTaskExecutor implements AsyncTaskExecutor, InitializingBean {
    /**
     * 系统最大负载
     */
    @Getter
    @Setter
    private int maxLoad = Runtime.getRuntime().availableProcessors() * 100 - 10;

    private final List<Consumer<AsyncTaskRecord>> finishListener = new ArrayList<>();
    private final List<Consumer<AsyncTaskRecord>> failedListener = new ArrayList<>();
    private final List<Consumer<AsyncTaskRecord>> startListener = new ArrayList<>();

    private final AtomicInteger currentLoad = new AtomicInteger(0);

    @Getter
    private final AsyncTaskReceiver taskReceiver;

    private final Map<String, AsyncTaskFactory> taskFactories = new ConcurrentHashMap<>();

    private final Map<Long, AsyncTask> runningTask = new ConcurrentHashMap<>();

    // 负载清算队列，当异步任务完成时
    private final BlockingQueue<Integer> loadCleanQueue = new LinkedBlockingQueue<>();

    private final Lock lock = new ReentrantLock();

    private boolean running = false;

    private int threadCount = 0;
    private final Executor threadPool = new ThreadPoolExecutor(
            1,
            1024,
            10,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
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
     * @param taskReceiver      任务接收函数（接收下发到的任务）
     */
    public DefaultAsyncTaskExecutor(AsyncTaskReceiver taskReceiver) {
        this.taskReceiver = taskReceiver;
    }

    /**
     * 创建一个基于redis的List作为消息队列接受任务的执行器
     */
    @Autowired
    public DefaultAsyncTaskExecutor(RedisTemplate<String, Object> redisTemplate) {
         this(new AsyncTaskReceiver() {
             private boolean isInterrupt = false;
             @Override
             public AsyncTaskRecord get() {
                 while (true) {
                     if (isInterrupt) {
                         return null;
                     }
                     try {
                         Object o = redisTemplate.opsForList().rightPop(AsyncTaskConstants.RedisKey.TASK_QUEUE, 3, TimeUnit.SECONDS);
                         if (o == null) {
                             Thread.sleep(100);
                             continue;
                         }
                         if (o instanceof String) {
                             return MapperHolder.parseJson((String) o, AsyncTaskRecord.class);
                         } else if (o instanceof AsyncTaskRecord) {
                             return (AsyncTaskRecord) o;
                         } else {
                             throw new IllegalArgumentException("任务反序列化失败");
                         }
                     } catch (Throwable e) {
                         throw new RuntimeException("任务接受出错:" + e.getMessage(), e);
                     }
                 }
             }

             @Override
             public void start() {
                 isInterrupt = false;
             }

             @Override
             public void interrupt() {
                 isInterrupt = true;
             }
         });
    }

    @Override
    public void registerFactory(AsyncTaskFactory factory) {
        if (taskFactories.containsKey(factory.getTaskType())) {
            throw new IllegalArgumentException("任务类型 " + factory.getTaskType() + " 的任务工厂已被注册");
        }
        taskFactories.put(factory.getTaskType(), factory);
    }

    @Override
    public boolean interrupt(Long executeId) {
        AsyncTask asyncTask = runningTask.get(executeId);
        if (asyncTask != null) {
            asyncTask.interrupt();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getCurrentLoad() {
        updateCurrentLoad();
        return currentLoad.get();
    }

    /**
     * 开始接受任务
     */
    @Override
    public synchronized void start() {
        if (running) {
            throw new IllegalArgumentException("已经启动了");
        }
        running = true;
        threadPool.execute(() -> {
            taskReceiver.start();
            while (true) {
                try {
                    try {
                        // 等待负载值降低到最大限度以下
                        waitLoad();
                    } catch (InterruptedException e) {
                        log.error("负载判断等待被中断", e);
                        continue;
                    }

                    // 接受一个任务
                    if (!isRunning()) {
                        return;
                    }
                    TaskContext taskContext = receiveTask();
                    if (taskContext == null) {
                        return;
                    }
                    if (!isRunning()) {
                        return;
                    }

                    // 提交执行
                    submitExecute(taskContext);
                } catch (Throwable e) {
                    log.error("任务接受与提交出错", e);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignore) {}
                }
            }
        });
    }

    /**
     * 提交任务执行
     */
    private void submitExecute(TaskContext taskContext) {
        AsyncTask asyncTask = taskContext.task;
        AsyncTaskRecord record = taskContext.record;

        // 更新负载记录值
        int cpuOverhead = Optional.ofNullable(record.getCpuOverhead()).orElse(0);
        if (cpuOverhead > 0) {
            currentLoad.addAndGet(cpuOverhead);
        }

        threadPool.execute(() -> {
            emit(startListener, record);

            runningTask.put(taskContext.record.getId(), asyncTask);

            Path logPath = PathUtils.getLogDirectory().resolve("async_task_" + record.getId() + ".log").toAbsolutePath();
            log.info("创建任务日志: {}", logPath);
            try {
                FileUtils.createParentDirectory(logPath);
            } catch (IOException e) {
                log.error("创建日志目录失败: ", e);
            }
            try (OutputStream logOutput = Files.newOutputStream(logPath)) {
                asyncTask.execute(logOutput);
                emit(finishListener, record);
            } catch (Throwable e) {
                emit(failedListener, record);
                log.error("异步任务{}执行出错", record.getId(), e);
            } finally {
                // 任务完成，添加负载值到待清算的队列
                if (cpuOverhead > 0) {
                    loadCleanQueue.add(cpuOverhead);
                }
                runningTask.remove(taskContext.record.getId());
            }
        });
    }

    /**
     * 等待负载值降低到最大值以下
     */
    private void waitLoad() throws InterruptedException {
        // 检查是否过载，若已过载，则进入负载清算程序，将已完成的负载值从当前负载中移除
        if (!testLoad()) {
            // 第一次清算，直到没有可清算的负载导致阻塞 或 负载值降低到最大值以下
            do {
                Integer take = loadCleanQueue.take();
                currentLoad.addAndGet(take * -1);
            } while (!testLoad());

            // 再把剩余可清算的负载也清算了
            updateCurrentLoad();
        }
    }

    /**
     * 更新当前负载值
     */
    private void updateCurrentLoad() {
        lock.lock();
        try {
            while (!loadCleanQueue.isEmpty()) {
                Integer take = loadCleanQueue.take();
                currentLoad.addAndGet(take * -1);
            }
        } catch (InterruptedException ignore) {
        } finally {
            lock.unlock();
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
        if (record.getId() == null) {
            record.setId(IdUtil.getId());
        }
        AsyncTaskFactory asyncTaskFactory = taskFactories.get(record.getTaskType());

        if (asyncTaskFactory == null) {
            throw new IllegalArgumentException("找不到任务类型为 " + record.getTaskType() + " 的任务工厂");
        }

        AsyncTask task = asyncTaskFactory.createTask(record.getParams());
        return new TaskContext(task, record);

    }

    private void emit(List<Consumer<AsyncTaskRecord>> listener, AsyncTaskRecord record) {
        for (Consumer<AsyncTaskRecord> consumer : listener) {
            try {
                consumer.accept(record);
            } catch (Throwable e) {
                log.error("任务事件监听器执行错误",e);
            }
        }
    }

    @Override
    public void addTaskStartListener(Consumer<AsyncTaskRecord> listener) {
        startListener.add(listener);
    }

    @Override
    public void addTaskFailedListener(Consumer<AsyncTaskRecord> listener) {
        failedListener.add(listener);
    }

    @Override
    public void addTaskFinishListener(Consumer<AsyncTaskRecord> listener) {
        finishListener.add(listener);
    }

    @AllArgsConstructor
    private static class TaskContext {
        public AsyncTask task;
        public AsyncTaskRecord record;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!running) {
            log.info("任务执行器启动");
            start();
        }
    }

    @Override
    @EventListener(ContextClosedEvent.class)
    public synchronized void stop() {
        if (running) {
            log.info("任务执行器停止");
            running = false;
            taskReceiver.interrupt();
            synchronized (runningTask) {
                for (Map.Entry<Long, AsyncTask> entry : runningTask.entrySet()) {
                    try {
                        log.info("任务停止: {}", entry.getKey());
                        entry.getValue().interrupt();
                    } catch (Throwable e) {
                        log.error("任务停止出错", e);
                    }
                }
            }
        }

    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
