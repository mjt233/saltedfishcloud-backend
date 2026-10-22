package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import com.sfc.cloud.task.worker.protocol.TaskWorkerRegisterParam;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务 Worker 运行时生命周期。
 *
 * <p>由 Spring 容器在 Web 容器启动完成后自动启动（{@link SmartLifecycle} 默认 phase），负责：</p>
 * <ol>
 *     <li>向 sfc-core 注册本实例（能力、负载、回调地址），并按 {@code heartbeat-interval} 续约；</li>
 *     <li>以单线程长轮询认领任务，按 {@code max-concurrent-tasks} 控制并发，交给任务线程池执行；</li>
 *     <li>以约 1 秒周期驱动 {@link SfcTaskReporter} 上报进度与日志；</li>
 *     <li>优雅停机：停止认领 → 注销 worker → 对运行中任务上报放弃（任务回到队列）→ 最多等待数秒
 *         让执行线程退出。</li>
 * </ol>
 */
@Slf4j
class SfcTaskWorkerRuntime implements SmartLifecycle {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 微服务 worker 类型，对应 core 的 {@code TaskWorkerKind.REMOTE_SERVICE}
     */
    private static final String REMOTE_SERVICE_KIND = "REMOTE_SERVICE";

    /**
     * 停机时等待执行线程退出的最长时间（秒）
     */
    private static final long SHUTDOWN_AWAIT_SECONDS = 3L;

    /**
     * 停机时等待认领线程退出的最长时间（毫秒）
     */
    private static final long CLAIM_THREAD_JOIN_MILLIS = 2000L;

    /**
     * 认领循环的空闲休眠时间（毫秒）
     */
    private static final long CLAIM_IDLE_SLEEP_MILLIS = 1000L;

    /**
     * 进度/日志上报周期（毫秒）
     */
    private static final long REPORT_INTERVAL_MILLIS = 1000L;

    /**
     * 心跳间隔下限（毫秒）
     */
    private static final long MIN_HEARTBEAT_MILLIS = 1000L;

    /**
     * core 侧 worker 注册信息有效期（秒），用于心跳间隔告警
     */
    private static final long CORE_WORKER_TTL_SECONDS = 30L;

    /**
     * 配置项
     */
    private final SfcTaskWorkerProperties properties;

    /**
     * 内部协议客户端
     */
    private final SfcTaskWorkerClient client;

    /**
     * 运行中任务注册表
     */
    private final SfcTaskWorkerRegistry registry;

    /**
     * 任务执行器
     */
    private final SfcTaskExecutionRunner runner;

    /**
     * 执行上报器
     */
    private final SfcTaskReporter reporter;

    /**
     * 当前实例身份信息
     */
    private final SfcTaskWorkerIdentity identity;

    /**
     * 是否处于运行中
     */
    private volatile boolean running;

    /**
     * 任务执行线程池
     */
    private volatile ExecutorService taskExecutor;

    /**
     * 心跳与上报调度器
     */
    private volatile ScheduledExecutorService scheduler;

    /**
     * 认领线程
     */
    private volatile Thread claimThread;

    /**
     * 构造运行时（由自动装配创建）。
     *
     * @param properties 配置项
     * @param client     内部协议客户端
     * @param registry   运行中任务注册表
     * @param runner     任务执行器
     * @param reporter   执行上报器
     * @param identity   当前实例身份信息
     */
    SfcTaskWorkerRuntime(SfcTaskWorkerProperties properties, SfcTaskWorkerClient client,
                         SfcTaskWorkerRegistry registry, SfcTaskExecutionRunner runner,
                         SfcTaskReporter reporter, SfcTaskWorkerIdentity identity) {
        this.properties = properties;
        this.client = client;
        this.registry = registry;
        this.runner = runner;
        this.reporter = reporter;
        this.identity = identity;
    }

    /**
     * 启动 Worker：注册、开启心跳/上报调度与认领线程。
     *
     * <p>注册失败不会阻断应用启动（core 可能尚未就绪），由后续心跳自动重试恢复。</p>
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        int maxConcurrent = Math.max(1, properties.getMaxConcurrentTasks());
        this.taskExecutor = Executors.newFixedThreadPool(maxConcurrent,
                new NamedThreadFactory("sfc-task-worker-task"));
        this.scheduler = Executors.newScheduledThreadPool(2,
                new NamedThreadFactory("sfc-task-worker-scheduler"));

        // 启动注册：失败也只告警，心跳会持续重试
        registerQuietly();

        long heartbeatMillis = normalizeHeartbeatMillis();
        scheduler.scheduleWithFixedDelay(this::heartbeatQuietly, heartbeatMillis, heartbeatMillis,
                TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::tickQuietly, REPORT_INTERVAL_MILLIS, REPORT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);

        this.claimThread = new Thread(this::claimLoop, "sfc-task-worker-claim");
        this.claimThread.setDaemon(true);
        this.claimThread.start();

        log.info("{}启动完成：workerId={}, 服务名={}, 回调地址={}, 任务类型={}, 最大并发={}, 心跳间隔={}ms",
                LOG_PREFIX, identity.getWorkerId(), identity.getServiceName(), identity.getCallbackBaseUrl(),
                registry.getTaskTypes(), maxConcurrent, heartbeatMillis);
    }

    /**
     * 停止 Worker：停止认领 → 注销 → 放弃运行中任务 → 等待执行线程退出。
     */
    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        // 1. 停止认领线程（可能正阻塞在长轮询上，中断后退出）
        Thread claim = this.claimThread;
        this.claimThread = null;
        if (claim != null) {
            claim.interrupt();
            joinQuietly(claim);
        }

        // 2. 停止心跳与上报调度
        ScheduledExecutorService currentScheduler = this.scheduler;
        this.scheduler = null;
        if (currentScheduler != null) {
            currentScheduler.shutdownNow();
        }

        // 3. 注销 worker
        try {
            client.unregister(identity.getWorkerId());
        } catch (Throwable e) {
            log.warn("{}注销 worker[{}] 失败: {}", LOG_PREFIX, identity.getWorkerId(), describe(e));
        }

        // 4. 对运行中任务上报放弃，任务会被 core 重置回等待中交给其他执行者
        List<Long> abandonedTaskIds = new ArrayList<>();
        for (SfcTaskExecution execution : registry.getRunningExecutions()) {
            try {
                execution.requestInterrupt();
                registry.unregisterExecution(execution.getTaskId());
                reporter.reportAbandon(execution);
                abandonedTaskIds.add(execution.getTaskId());
            } catch (Throwable e) {
                log.warn("{}放弃任务[{}]失败: {}", LOG_PREFIX, execution.getTaskId(), describe(e));
            }
        }

        // 5. 关闭任务线程池，最多等待数秒让处理器响应中断后退出
        ExecutorService executor = this.taskExecutor;
        this.taskExecutor = null;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("{}已停止，已放弃并重新排队的任务: {}", LOG_PREFIX, abandonedTaskIds);
    }

    /**
     * 是否处于运行中。
     *
     * @return 运行中返回 true
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 认领循环：有容量时以长轮询认领任务，交给任务线程池执行。
     */
    private void claimLoop() {
        log.info("{}认领线程已启动", LOG_PREFIX);
        while (running) {
            try {
                if (!registry.hasHandlers()) {
                    sleepQuietly();
                    continue;
                }
                if (!registry.tryAcquireSlot()) {
                    sleepQuietly();
                    continue;
                }
                boolean slotReleased = false;
                try {
                    TaskDispatchInfo dispatchInfo = client.claim(identity.getWorkerId(),
                            new ArrayList<>(registry.getTaskTypes()), properties.getClaimWaitSeconds());
                    if (dispatchInfo == null) {
                        continue;
                    }
                    if (!running) {
                        abandonTask(dispatchInfo, "执行者停机，放弃任务执行权");
                        continue;
                    }
                    SfcTaskHandler handler = registry.getHandler(dispatchInfo.taskType());
                    if (handler == null) {
                        log.warn("{}认领到本实例不支持的任务类型[{}]，任务id={}，已上报放弃",
                                LOG_PREFIX, dispatchInfo.taskType(), dispatchInfo.id());
                        abandonTask(dispatchInfo, "本执行者不支持任务类型: " + dispatchInfo.taskType());
                        continue;
                    }
                    ExecutorService executor = this.taskExecutor;
                    if (executor == null) {
                        abandonTask(dispatchInfo, "执行者停机，放弃任务执行权");
                        continue;
                    }
                    // 先创建并登记执行上下文：排队中的任务也能被中断与停机放弃流程覆盖；
                    // 登记完成后把认领占用的容量占位转为「运行中」计数
                    SfcTaskExecution execution = new SfcTaskExecution(dispatchInfo, identity.getWorkerId());
                    registry.registerExecution(execution);
                    registry.releaseSlot();
                    slotReleased = true;
                    try {
                        executor.execute(() -> runner.run(execution, handler));
                    } catch (RuntimeException e) {
                        registry.unregisterExecution(dispatchInfo.id());
                        log.warn("{}任务[{}]提交执行线程池失败，已上报放弃: {}",
                                LOG_PREFIX, dispatchInfo.id(), describe(e));
                        abandonTask(dispatchInfo, "执行线程池拒绝任务，放弃任务执行权");
                    }
                } finally {
                    if (!slotReleased) {
                        registry.releaseSlot();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable e) {
                if (!running || Thread.currentThread().isInterrupted()) {
                    break;
                }
                log.warn("{}认领任务失败，1 秒后重试: {}", LOG_PREFIX, describe(e));
                reRegisterIfExpired(e);
                try {
                    sleepQuietly();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("{}认领线程已退出", LOG_PREFIX);
    }

    /**
     * 上报放弃一个已认领但无法执行的任务（不支持的类型、停机、线程池拒绝等），任务会被 core 重置回队列。
     *
     * @param dispatchInfo 下发的任务信息
     * @param message      放弃原因说明
     */
    private void abandonTask(TaskDispatchInfo dispatchInfo, String message) {
        try {
            client.reportAbandon(new TaskAbandonParam(dispatchInfo.id(), identity.getWorkerId(), message));
        } catch (Throwable e) {
            log.warn("{}上报放弃任务[{}]失败: {}", LOG_PREFIX, dispatchInfo.id(), describe(e));
        }
    }

    /**
     * 心跳：重新注册以续约并刷新负载与任务类型。
     */
    private void heartbeatQuietly() {
        if (!running) {
            return;
        }
        registerQuietly();
    }

    /**
     * 向 core 注册/续约当前 worker，失败仅告警。
     */
    private void registerQuietly() {
        try {
            registerSelf();
        } catch (Throwable e) {
            log.warn("{}向 sfc-core 注册/续约失败，将在下个心跳周期重试: {}", LOG_PREFIX, describe(e));
        }
    }

    /**
     * 向 core 注册/续约当前 worker。
     */
    private void registerSelf() {
        client.register(new TaskWorkerRegisterParam(
                identity.getWorkerId(),
                REMOTE_SERVICE_KIND,
                identity.getServiceName(),
                identity.getCallbackBaseUrl(),
                new ArrayList<>(registry.getTaskTypes()),
                properties.getMaxLoad(),
                registry.getCurrentLoad()));
    }

    /**
     * 认领失败后，若原因是 worker 注册已过期（core 返回 410），立即补一次注册。
     *
     * @param e 认领异常
     */
    private void reRegisterIfExpired(Throwable e) {
        if (!(e instanceof SfcJsonException sfcException)) {
            return;
        }
        SfcJsonResult<?> result = sfcException.getRes();
        if (result != null && result.getCode() == 410) {
            log.info("{}worker 注册信息已过期，立即重新注册", LOG_PREFIX);
            registerQuietly();
        }
    }

    /**
     * 驱动一次进度/日志上报，异常不外抛以免终止调度。
     */
    private void tickQuietly() {
        try {
            reporter.tick();
        } catch (Throwable e) {
            log.warn("{}周期上报异常: {}", LOG_PREFIX, describe(e));
        }
    }

    /**
     * 归一化心跳间隔：不低于 1 秒；接近/超过 core 的 30 秒有效期时给出告警。
     *
     * @return 心跳间隔毫秒数
     */
    private long normalizeHeartbeatMillis() {
        Duration configured = properties.getHeartbeatInterval();
        long millis = configured == null || configured.isZero() || configured.isNegative()
                ? TimeUnit.SECONDS.toMillis(10L)
                : configured.toMillis();
        long normalized = Math.max(MIN_HEARTBEAT_MILLIS, millis);
        if (normalized >= TimeUnit.SECONDS.toMillis(CORE_WORKER_TTL_SECONDS)) {
            log.warn("{}心跳间隔 {}ms 不小于 core 的注册有效期 {} 秒，可能被判定为失联并触发任务回收，"
                    + "建议配置为 10 秒左右", LOG_PREFIX, normalized, CORE_WORKER_TTL_SECONDS);
        }
        return normalized;
    }

    /**
     * 等待线程结束，忽略中断。
     *
     * @param thread 目标线程
     */
    private static void joinQuietly(Thread thread) {
        try {
            thread.join(CLAIM_THREAD_JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 按认领空闲间隔休眠。
     *
     * @throws InterruptedException 线程被中断时抛出
     */
    private static void sleepQuietly() throws InterruptedException {
        Thread.sleep(CLAIM_IDLE_SLEEP_MILLIS);
    }

    /**
     * 异常简要描述：优先使用异常消息，消息为空时回退异常类型名。
     *
     * @param e 异常
     * @return 简要描述
     */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    /**
     * 带名称前缀的守护线程工厂，便于线上排查线程归属。
     */
    private static final class NamedThreadFactory implements ThreadFactory {

        /**
         * 线程名前缀
         */
        private final String prefix;

        /**
         * 线程序号
         */
        private final AtomicInteger sequence = new AtomicInteger();

        /**
         * 构造线程工厂。
         *
         * @param prefix 线程名前缀
         */
        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
