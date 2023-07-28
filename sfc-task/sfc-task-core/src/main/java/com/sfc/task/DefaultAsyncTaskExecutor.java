package com.sfc.task;


import com.sfc.constant.MQTopic;
import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressDetector;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.xiaotao.saltedfishcloud.service.MQService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.PathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 异步任务执行器
 */
@Slf4j
@Component
public class DefaultAsyncTaskExecutor implements AsyncTaskExecutor {
    /**
     * 系统最大负载
     */
    @Getter
    @Setter
    private int maxLoad = Runtime.getRuntime().availableProcessors() * 100 - 10;

    @Autowired
    private AsyncTaskLogRecordRepo logRepo;

    @Autowired
    private ProgressDetector progressDetector;

    @Autowired
    private UserService userService;

    @Autowired
    private MQService mqService;

    private final static byte[] WRAP_BYTES = "\n".getBytes(StandardCharsets.UTF_8);

    private final List<Consumer<AsyncTaskRecord>> finishListener = new ArrayList<>();
    private final List<Consumer<AsyncTaskRecord>> failedListener = new ArrayList<>();
    private final List<Consumer<AsyncTaskRecord>> startListener = new ArrayList<>();
    private final List<Consumer<AsyncTaskRecord>> unsupportedListener = new ArrayList<>();
    private final List<Consumer<AsyncTaskRecord>> taskExitListener = new ArrayList<>();

    private final AtomicInteger currentLoad = new AtomicInteger(0);

    @Getter
    private final AsyncTaskReceiver taskReceiver;

    private final Map<String, AsyncTaskFactory> taskFactories = new ConcurrentHashMap<>();

    private final Map<Long, AsyncTask> runningTask = new ConcurrentHashMap<>();

    private final Set<Long> giveUpRecord = new HashSet<>();

    // 负载清算队列，当异步任务完成时
    private final BlockingQueue<Integer> loadCleanQueue = new LinkedBlockingQueue<>();

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
         this(RedisTaskReceiver.builder()
                 .redisTemplate(redisTemplate)
                 .build());
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
    @EventListener(ApplicationReadyEvent.class)
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
                        break;
                    }
                    TaskContext taskContext = receiveTask();
                    if (taskContext == null) {
                        break;
                    }
                    if (!isRunning()) {
                        break;
                    }

                    // 提交执行
                    submitExecute(taskContext);
                } catch (Throwable e) {
                    log.error("任务接受与提交出错", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignore) {}
                }
            }
            log.info("任务接收线程退出");
        });
    }

    private Path getLogPath(Long taskId) {
        return PathUtils.getLogDirectory().resolve("async_task_" + taskId + ".log").toAbsolutePath();
    }

    /**
     * 提交任务执行
     */
    private void submitExecute(TaskContext taskContext) {
        AsyncTask asyncTask = taskContext.task;
        AsyncTaskRecord record = taskContext.record;
        record.setStatus(AsyncTaskConstants.Status.RUNNING);

        // 更新负载记录值
        int cpuOverhead = Optional.ofNullable(record.getCpuOverhead()).orElse(0);
        if (cpuOverhead > 0) {
            currentLoad.addAndGet(cpuOverhead);
        }
        Long recordId = taskContext.record.getId();
        threadPool.execute(() -> {
            emit(startListener, record);
            runningTask.put(recordId, asyncTask);

            // 若任务记录状态被修改为已取消，则说明该任务是个之前停留在队列中排队但现在已经被取消的任务
            if (AsyncTaskConstants.Status.CANCEL.equals(record.getStatus())) {
                log.info("忽略处理任务队列中被取消的任务: {}-{}",record.getName(), recordId);
                runningTask.remove(recordId);
                // 任务完成，添加负载值到待清算的队列
                if (cpuOverhead > 0) {
                    loadCleanQueue.add(cpuOverhead);
                }
                return;
            }

            // 初始化日志输出目录
            Path logPath = getLogPath(record.getId());
            log.info("创建任务日志: {}", logPath);
            try {
                FileUtils.createParentDirectory(logPath);
            } catch (IOException e) {
                log.error("创建日志目录失败: ", e);
            }

            // 创建日志输出流
            try (OutputStream logOutput = Files.newOutputStream(logPath);
                PipedInputStream pi = new PipedInputStream(8192);
                PipedOutputStream po = new PipedOutputStream()
            ) {
                pi.connect(po);
                Semaphore semaphore = new Semaphore(1);

                // 绑定上下文用户信息
                SecureUtils.bindUser(userService.getUserById(record.getUid().intValue()));
                // 添加进度事件监听
                progressDetector.addObserve(asyncTask::getProgress, record.getId() + "");

                // 将获取到的日志推送到消息队列和写到文件
                semaphore.acquire();
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(pi, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logOutput.write(line.getBytes(StandardCharsets.UTF_8));
                            logOutput.write(WRAP_BYTES);
                            mqService.push(MQTopic.Prefix.ASYNC_TASK_LOG + recordId, line);
                        }
                    } catch (IOException e) {
                        log.error("[异步任务执行器]推送异步任务日志出错", e);
                    } finally {
                        semaphore.release();
                        try {
                            // 消息队列日志保留5秒后销毁
                            Thread.sleep(5000);
                            mqService.destroyQueue(MQTopic.Prefix.ASYNC_TASK_LOG + recordId);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }).start();

                try {
                    // 执行任务本体
                    asyncTask.execute(po);
                } finally {
                    // 关闭流
                    po.close();

                    // 确保日志写入文件完成后再关闭文件流
                    semaphore.acquire();
                    logOutput.close();
                    if (!giveUpRecord.contains(recordId)) {
                        emit(finishListener, record);
                    }
                }
            } catch (Throwable e) {
                if (!giveUpRecord.contains(recordId)) {
                    emit(failedListener, record);
                }
                log.error("异步任务{}执行出错", record.getId(), e);
            } finally {
                try {
                    // 任务完成，添加负载值到待清算的队列
                    if (cpuOverhead > 0) {
                        loadCleanQueue.add(cpuOverhead);
                    }

                    // 若任务未被因故障转移而放弃，则保存任务日志
                    if (!giveUpRecord.contains(recordId)) {
                        this.saveLog(record, logPath);
                    }

                    // 移除任务实例
                    runningTask.remove(recordId);
                    // 移除进度监听
                    this.removeProgressDetect(record);
                } finally {
                    synchronized (giveUpRecord) {
                        giveUpRecord.remove(recordId);
                    }
                    emit(taskExitListener, record);
                }
            }
        });
    }

    private void removeProgressDetect(AsyncTaskRecord record) {
        try {
            progressDetector.removeObserve(record.getId() + "");
        } catch (Exception e) {
            log.error("移除进度监听出错: ", e);
        }

    }

    /**
     * 保存日志到数据库
     * @param record    任务记录
     * @param logPath   日志路径
     */
    private void saveLog(AsyncTaskRecord record, Path logPath) {
        try {
            AsyncTaskLogRecord logRecord = new AsyncTaskLogRecord();
            logRecord.setUid(record.getUid());
            logRecord.setTaskId(record.getId());
            try (InputStream is = Files.newInputStream(logPath)) {
                logRecord.setLogInfo(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
            } catch (IOException e) {
                logRecord.setLogInfo("日志获取出错：" + e.getMessage());
                log.error("日志获取出错：", e);
            }
            logRepo.save(logRecord);
            Files.deleteIfExists(logPath);
        } catch (Exception e) {
            log.error("日志保存出错：", e);
        }
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
    private synchronized void updateCurrentLoad() {
        try {
            while (!loadCleanQueue.isEmpty()) {
                Integer take = loadCleanQueue.take();
                currentLoad.addAndGet(take * -1);
            }
        } catch (InterruptedException ignore) {
        }
    }

    @Override
    public String getLog(Long taskId) throws IOException {
        Path logPath = getLogPath(taskId);
        if (Files.exists(logPath)) {
            return ResourceUtils.resourceToString(new PathResource(logPath));
        }
        return null;
    }

    /**
     * 测试是否满载过超载
     */
    private boolean testLoad() {
        return currentLoad.get() <= maxLoad;
    }

    protected TaskContext receiveTask() {
        AsyncTaskRecord record = taskReceiver.get();
        if (record == null) {
            return null;
        }
        if (record.getId() == null) {
            record.setId(IdUtil.getId());
        }
        AsyncTaskFactory asyncTaskFactory = taskFactories.get(record.getTaskType());

        if (asyncTaskFactory == null) {
            emit(unsupportedListener, record);
            throw new IllegalArgumentException("找不到任务类型为 " + record.getTaskType() + " 的任务工厂");
        }

        AsyncTask task = asyncTaskFactory.createTask(record.getParams(), record);
        return new TaskContext(task, record);

    }

    /**
     * 处理事件
     */
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
    public ProgressRecord getProgress(Long taskId) throws IOException {
        return progressDetector.getRecord(taskId + "");
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
    public synchronized List<Long> stop() {
        List<Long> interruptIds = new ArrayList<>();
        if (running) {
            log.info("任务执行器停止");
            running = false;
            taskReceiver.interrupt();
            synchronized (runningTask) {
                for (Map.Entry<Long, AsyncTask> entry : runningTask.entrySet()) {
                    try {
                        log.info("任务放弃: {}", entry.getKey());
                        giveUp(entry.getKey());
                        interruptIds.add(entry.getKey());
                    } catch (Throwable e) {
                        log.error("任务放弃出错", e);
                    }
                }
            }
        }
        return interruptIds;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public AsyncTask getTask(Long taskId) {
        return runningTask.get(taskId);
    }

    @Override
    public Collection<Long> getRunningTask() {
        return runningTask.keySet();
    }

    @Override
    public void giveUp(Long taskId) {
        AsyncTask asyncTask = runningTask.get(taskId);
        if (asyncTask == null) {
            return;
        }
        synchronized (giveUpRecord) {
            giveUpRecord.add(taskId);
        }
        try {
            asyncTask.interrupt();
        } catch (Throwable e) {
            log.error("任务放弃时中断异常：", e);
        }
    }

    @Override
    public void addUnsupportedListener(Consumer<AsyncTaskRecord> listener) {
        this.unsupportedListener.add(listener);
    }

    @Override
    public AsyncTaskReceiver getReceiver() {
        return taskReceiver;
    }

    @Override
    public void addTaskExitListener(Consumer<AsyncTaskRecord> listener) {
        taskExitListener.add(listener);
    }
}
