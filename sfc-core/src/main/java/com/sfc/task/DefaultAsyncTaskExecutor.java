package com.sfc.task;

import com.sfc.cloud.data.id.SfcIdUtil;
import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressDetector;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.sfc.task.constants.TaskMQTopic.Get.ASYNC_TASK_LOG;

/**
 * 默认的异步任务执行器。
 * <p>本执行器只负责「执行被认领到的任务」以及任务执行过程（日志、进度、负载）管理。
 * 从队列中按任务类型认领任务的工作由 {@link com.sfc.task.worker.LocalTaskWorker} 完成，
 * 这样 core 本地任务与微服务任务共享同一套调度与生命周期语义。</p>
 */
@Slf4j
public class DefaultAsyncTaskExecutor implements AsyncTaskExecutor {
    /**
     * 系统最大负载
     */
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
    private final List<Consumer<AsyncTaskRecord>> taskExitListener = new ArrayList<>();

    private final AtomicInteger currentLoad = new AtomicInteger(0);

    private final Map<String, AsyncTaskFactory> taskFactories = new ConcurrentHashMap<>();

    private final Map<Long, AsyncTask> runningTask = new ConcurrentHashMap<>();

    /**
     * 因故障转移被放弃执行的任务id
     */
    private final Set<Long> giveUpRecord = new HashSet<>();

    /**
     * 收到了中断请求的任务id
     */
    private final Set<Long> cancelledRecord = new HashSet<>();

    // 负载清算队列，当异步任务完成时
    private final BlockingQueue<Integer> loadCleanQueue = new LinkedBlockingQueue<>();

    private volatile boolean running = false;

    private int threadCount = 0;

    private final Executor threadPool = new ThreadPoolExecutor(
            1,
            1024,
            10,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                String name = "AsyncTaskExecutor-" + this.hashCode() % 10000 + "-" + threadCount++;
                Thread thread = new Thread(r);
                thread.setName(name);
                return thread;
            }
    );

    @Override
    public void registerFactory(AsyncTaskFactory factory) {
        if (taskFactories.containsKey(factory.getTaskType())) {
            throw new IllegalArgumentException("任务类型 " + factory.getTaskType() + " 的任务工厂已被注册");
        }
        taskFactories.put(factory.getTaskType(), factory);
        log.info("注册异步任务工厂: {}", factory.getTaskType());
    }

    @Override
    public Collection<String> getRegisteredTaskTypes() {
        return new HashSet<>(taskFactories.keySet());
    }

    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (running) {
            throw new IllegalArgumentException("已经启动了");
        }
        running = true;
        log.info("本地异步任务执行器启动，最大负载: {}，支持任务类型: {}", maxLoad, taskFactories.keySet());
    }

    @Override
    public void execute(AsyncTaskRecord record) {
        AsyncTaskContext context = buildTaskContext(record);
        if (context == null) {
            return;
        }
        submitExecute(context, false);
    }

    @Override
    public void executeClaimed(AsyncTaskRecord record) {
        AsyncTaskContext context = buildTaskContext(record);
        if (context == null) {
            return;
        }
        submitExecute(context, true);
    }

    /**
     * 根据任务记录创建任务实例。
     *
     * @param record 任务记录
     * @return 任务上下文；任务类型没有对应工厂时返回 null（任务会被置为失败）
     */
    private AsyncTaskContext buildTaskContext(AsyncTaskRecord record) {
        if (record.getId() == null) {
            record.setId(SfcIdUtil.getId());
        }
        AsyncTaskFactory asyncTaskFactory = taskFactories.get(record.getTaskType());
        if (asyncTaskFactory == null) {
            log.error("任务类型 {} 没有对应的任务工厂，任务 {}-{} 将置为失败",
                    record.getTaskType(), record.getName(), record.getId());
            record.setStatus(AsyncTaskConstants.Status.FAILED);
            emit(failedListener, record);
            return null;
        }
        AsyncTask task = asyncTaskFactory.createTask(record.getParams(), record);
        return new AsyncTaskContext(task, record);
    }

    /**
     * 提交任务执行。
     *
     * @param taskContext 任务上下文
     * @param updateLoad  是否将任务开销计入系统负载
     */
    private void submitExecute(AsyncTaskContext taskContext, boolean updateLoad) {
        AsyncTask asyncTask = taskContext.task;
        AsyncTaskRecord record = taskContext.record;
        record.setStatus(AsyncTaskConstants.Status.RUNNING);

        // 更新负载记录值
        int cpuOverhead = updateLoad ? Optional.ofNullable(record.getCpuOverhead()).orElse(0) : 0;
        if (cpuOverhead > 0) {
            currentLoad.addAndGet(cpuOverhead);
        }
        final int finalCpuOverhead = cpuOverhead;
        Long recordId = record.getId();
        threadPool.execute(() -> {
            emit(startListener, record);
            runningTask.put(recordId, asyncTask);

            // 若任务记录状态被修改为已取消，则说明该任务是个之前停留在队列中排队但现在已经被取消的任务
            if (AsyncTaskConstants.Status.CANCEL.equals(record.getStatus())) {
                log.info("忽略处理任务队列中被取消的任务: {}-{}", record.getName(), recordId);
                runningTask.remove(recordId);
                if (finalCpuOverhead > 0) {
                    loadCleanQueue.add(finalCpuOverhead);
                }
                emit(failedListener, record);
                return;
            }

            // 初始化日志输出目录
            Path logPath = getLogPath(recordId);
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

                // 绑定上下文用户信息
                UserPrincipal executeUser = UserPrincipal.from(userService.getUserById(record.getUid()));
                SecureUtils.bindUser(executeUser);
                // 添加进度事件监听
                progressDetector.addObserve(asyncTask::getProgress, recordId + "");

                // 将获取到的日志推送到消息队列和写到文件
                CompletableFuture<Void> asyncLogFuture = CompletableFuture.runAsync(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(pi, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logOutput.write(line.getBytes(StandardCharsets.UTF_8));
                            logOutput.write(WRAP_BYTES);
                            mqService.push(ASYNC_TASK_LOG(recordId), line);
                        }
                    } catch (IOException e) {
                        log.error("[异步任务执行器]推送异步任务日志出错", e);
                    } finally {
                        // 消息队列日志保留5秒后销毁
                        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                                .execute(() -> mqService.destroyQueue(ASYNC_TASK_LOG(recordId)));
                    }
                });

                try {
                    // 执行任务本体
                    asyncTask.execute(po);
                    record.setStatus(AsyncTaskConstants.Status.FINISH);
                    emit(finishListener, record);
                } finally {
                    // ============ 日志流关闭 ============
                    try {
                        po.close();
                        // 确保日志写入文件完成后再关闭文件流
                        asyncLogFuture.join();
                        logOutput.close();
                    } catch (Throwable ignore) {}
                }
            } catch (Throwable e) {
                // ============ 任务执行异常处理 ============
                // 出错时判断是否因为中断/故障转移导致的，设置对应的状态并抛出事件
                boolean abandoned = giveUpRecord.contains(recordId);
                if (abandoned) {
                    // 故障转移/停机放弃：任务状态由任务管理器统一回收（重置为等待中或置为失败），
                    // 这里不能回写状态，否则会覆盖回收结果导致任务丢失
                    log.info("任务{}-{}因故障转移被放弃执行，不更新任务状态", record.getName(), recordId);
                } else {
                    if (cancelledRecord.contains(recordId)) {
                        record.setStatus(AsyncTaskConstants.Status.CANCEL);
                    } else {
                        record.setStatus(AsyncTaskConstants.Status.FAILED);
                    }
                    log.error("异步任务{}执行出错", recordId, e);
                    emit(failedListener, record);
                }
            } finally {
                // ============ 数据清理类任务 ============
                SecureUtils.unbind();
                try {
                    // 任务完成，添加负载值到待清算的队列
                    if (finalCpuOverhead > 0) {
                        loadCleanQueue.add(finalCpuOverhead);
                    }

                    // 若任务未被因故障转移而放弃，则保存任务日志
                    if (!giveUpRecord.contains(recordId)) {
                        this.saveLog(record, logPath);
                    }

                    // 移除任务实例
                    runningTask.remove(recordId);
                    // 5s后延迟移除进度监听，确保任务结束后前端轮询还能获取到最后的进度信息
                    CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                            .execute(() -> this.removeProgressDetect(record));
                } finally {
                    synchronized (giveUpRecord) {
                        giveUpRecord.remove(recordId);
                    }
                    synchronized (cancelledRecord) {
                        cancelledRecord.remove(recordId);
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

    private Path getLogPath(Long taskId) {
        return PathUtils.getLogDirectory().resolve("async_task_" + taskId + ".log").toAbsolutePath();
    }

    @Override
    public String getLog(Long taskId) throws IOException {
        Path logPath = getLogPath(taskId);
        if (Files.exists(logPath)) {
            return Files.readString(logPath, StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * 处理事件
     */
    private void emit(List<Consumer<AsyncTaskRecord>> listener, AsyncTaskRecord record) {
        for (Consumer<AsyncTaskRecord> consumer : listener) {
            try {
                consumer.accept(record);
            } catch (Throwable e) {
                log.error("任务事件监听器执行错误", e);
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

    @Override
    public void addTaskExitListener(Consumer<AsyncTaskRecord> listener) {
        taskExitListener.add(listener);
    }

    @Override
    public ProgressRecord getProgress(Long taskId) throws IOException {
        return progressDetector.getRecord(taskId + "");
    }

    /**
     * 任务上下文
     */
    @AllArgsConstructor
    private static class AsyncTaskContext {
        public AsyncTask task;
        public AsyncTaskRecord record;
    }

    @Override
    public synchronized Collection<Long> stop() {
        List<Long> interruptIds = new ArrayList<>();
        if (running) {
            log.info("任务执行器停止");
            running = false;
            for (Long taskId : new ArrayList<>(runningTask.keySet())) {
                try {
                    log.info("任务放弃: {}", taskId);
                    giveUp(taskId);
                    interruptIds.add(taskId);
                } catch (Throwable e) {
                    log.error("任务放弃出错", e);
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
    public boolean interrupt(Long taskId) {
        AsyncTask asyncTask = runningTask.get(taskId);
        if (asyncTask == null) {
            return false;
        }
        synchronized (cancelledRecord) {
            cancelledRecord.add(taskId);
        }
        try {
            asyncTask.interrupt();
            return true;
        } catch (Throwable e) {
            log.error("任务中断异常：", e);
            return false;
        }
    }

    @Override
    public int getCurrentLoad() {
        updateCurrentLoad();
        return currentLoad.get();
    }

    @Override
    public int getMaxLoad() {
        return maxLoad;
    }

    @Override
    public void setMaxLoad(int maxLoad) {
        this.maxLoad = maxLoad;
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
}
