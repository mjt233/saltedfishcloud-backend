package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskClaimParam;
import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务 Worker 运行时生命周期：任务认领、执行调度、进度/日志上报驱动与优雅停机。
 *
 * <p>本类是宿主（sfc-core 内嵌执行者）与微服务共用的执行侧实现，由自动装配创建为单例
 * （也可由宿主按同样的依赖手工创建），并在 Web 容器启动完成后自动启动
 * （{@link SmartLifecycle} 默认 phase）。</p>
 *
 * <p>启动自检：内部接口鉴权未配置，或消费方未启用服务发现/负载均衡（无法定位 sfc-core）时记录 ERROR
 * 并跳过启动（应用继续运行）；停机时先停止认领，再把正在执行的任务上报放弃，任务会重新排队。</p>
 */
@Slf4j
public class SfcTaskWorkerRuntime implements SmartLifecycle {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 认领请求中的默认执行者角色：微服务实例
     */
    private static final String DEFAULT_WORKER_ROLE = "SERVICE";

    /**
     * 停机时等待执行线程退出的最长时间（秒）
     */
    private static final long SHUTDOWN_AWAIT_SECONDS = 3L;

    /**
     * 停机时等待认领线程退出的最长时间（毫秒）。
     * <p>认领请求是长轮询，线程可能正阻塞在 HTTP 调用上且不一定响应中断；必须等它退出后再放弃任务，
     * 否则退出瞬间新认领的任务可能来不及上报放弃，成为遗留的运行中任务。</p>
     */
    private static final long CLAIM_THREAD_JOIN_MILLIS = 15_000L;

    /**
     * 认领循环的空闲休眠时间（毫秒）
     */
    private static final long CLAIM_IDLE_SLEEP_MILLIS = 1000L;

    /**
     * 进度/日志上报周期（毫秒）
     */
    private static final long REPORT_INTERVAL_MILLIS = 1000L;

    /**
     * 满载时只认领紧急任务的最长等待秒数
     */
    private static final int MAX_URGENT_CLAIM_WAIT_SECONDS = 3;

    /**
     * 任务执行线程池的最大线程数
     */
    private static final int MAX_TASK_POOL_SIZE = 1024;

    /**
     * 任务执行线程池中超出核心线程数的空闲线程存活时间（秒）
     */
    private static final long TASK_POOL_KEEP_ALIVE_SECONDS = 60L;

    /**
     * 注册中心可见性自检的重试间隔（毫秒）
     */
    private static final long REGISTRATION_CHECK_INTERVAL_MILLIS = 1000L;

    /**
     * 注册中心可见性自检的最长等待时间（毫秒）
     */
    private static final long REGISTRATION_CHECK_TIMEOUT_MILLIS = 60_000L;

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
     * 服务发现客户端：用于自检「本实例已在注册中心可见」；消费方未启用服务发现时为 null
     */
    private final DiscoveryClient discoveryClient;

    /**
     * 是否处于运行中
     */
    private volatile boolean running;

    /**
     * 任务执行线程池
     */
    private volatile ExecutorService taskExecutor;

    /**
     * 进度/日志上报调度器
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
     * @param discoveryProvider 服务发现客户端提供者（消费方未启用服务发现时为空）
     */
    SfcTaskWorkerRuntime(SfcTaskWorkerProperties properties, SfcTaskWorkerClient client,
                         SfcTaskWorkerRegistry registry, SfcTaskExecutionRunner runner,
                         SfcTaskReporter reporter, SfcTaskWorkerIdentity identity,
                         ObjectProvider<DiscoveryClient> discoveryProvider) {
        this.properties = properties;
        this.client = client;
        this.registry = registry;
        this.runner = runner;
        this.reporter = reporter;
        this.identity = identity;
        this.discoveryClient = resolveDiscoveryClient(discoveryProvider);
    }

    /**
     * 解析服务发现客户端；不存在、不唯一或解析失败时返回 null（按未启用服务发现处理）。
     *
     * @param discoveryProvider 服务发现客户端提供者
     * @return 服务发现客户端，可能为 null
     */
    private static DiscoveryClient resolveDiscoveryClient(ObjectProvider<DiscoveryClient> discoveryProvider) {
        if (discoveryProvider == null) {
            return null;
        }
        try {
            return discoveryProvider.getIfUnique();
        } catch (Throwable e) {
            log.warn("{}解析服务发现客户端失败，将按未启用服务发现处理: {}", LOG_PREFIX, describe(e));
            return null;
        }
    }

    /**
     * 启动 Worker。内部接口鉴权未配置，或消费方未启用服务发现/负载均衡（无法定位 sfc-core）时禁用 Worker
     * 并记录 ERROR，应用继续运行；本方法不阻塞启动流程。
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (!client.isAuthConfigured()) {
            log.error("{}未配置内部接口鉴权密钥（sfc.internal.key / 环境变量 SFC_INTERNAL_KEY），"
                    + "无法调用 sfc-core 内部接口，Worker 已禁用（应用继续运行）", LOG_PREFIX);
            return;
        }
        if (discoveryClient == null || !client.isCoreLocatable()) {
            log.error("{}未启用服务发现/负载均衡能力（未找到 DiscoveryClient 或 LoadBalancerClient），"
                    + "无法定位 sfc-core，Worker 已禁用（应用继续运行）", LOG_PREFIX);
            return;
        }
        running = true;
        int maxConcurrent = Math.max(1, properties.getMaxConcurrentTasks());
        this.taskExecutor = new ThreadPoolExecutor(maxConcurrent, MAX_TASK_POOL_SIZE,
                TASK_POOL_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new SynchronousQueue<>(),
                new NamedThreadFactory("sfc-task-worker-task"));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("sfc-task-worker-scheduler"));
        scheduler.scheduleAtFixedRate(this::tickQuietly, REPORT_INTERVAL_MILLIS, REPORT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);

        this.claimThread = new Thread(this::claimLoop, "sfc-task-worker-claim");
        this.claimThread.setDaemon(true);
        this.claimThread.start();

        log.info("{}启动完成：role={}, serviceId={}, 任务类型={}, 最大并发={}, sfc-core=服务发现({})；"
                        + "认领线程正在确认本实例已在注册中心可见",
                LOG_PREFIX, resolveRole(), identity.getServiceId(), registry.getTaskTypes(),
                maxConcurrent, client.getCoreServiceId());
    }

    /**
     * 停止 Worker：停止认领、放弃运行中任务并等待执行线程退出；被放弃的任务会重新排队。
     */
    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        // 1. 停止认领线程（可能正阻塞在长轮询或注册可见性自检上，中断后退出）
        Thread claim = this.claimThread;
        this.claimThread = null;
        if (claim != null) {
            claim.interrupt();
            joinQuietly(claim);
        }

        // 2. 停止上报调度
        ScheduledExecutorService currentScheduler = this.scheduler;
        this.scheduler = null;
        if (currentScheduler != null) {
            currentScheduler.shutdownNow();
        }

        // 3. 对运行中任务上报放弃，任务会被 core 重置回等待中交给其他执行者
        List<Long> abandonedTaskIds = new ArrayList<>(abandonRunningTasks());

        // 4. 关闭任务线程池，最多等待数秒让处理器响应中断后退出
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

        // 5. 关闭线程池后做最后一次清扫：停机窗口内可能仍有刚登记的执行上下文
        List<Long> lateAbandonedTaskIds = abandonRunningTasks();
        if (!lateAbandonedTaskIds.isEmpty()) {
            log.warn("{}停机窗口内又登记了 {} 个执行上下文，已补报放弃: {}",
                    LOG_PREFIX, lateAbandonedTaskIds.size(), lateAbandonedTaskIds);
            abandonedTaskIds.addAll(lateAbandonedTaskIds);
        }

        log.info("{}已停止，已放弃并重新排队的任务: {}", LOG_PREFIX, abandonedTaskIds);
    }

    /**
     * 放弃当前登记的全部运行中任务并上报。
     *
     * <p>放弃后的任务由调度中心按故障转移规则处理：普通任务重新排队，临时任务置为失败。</p>
     *
     * @return 已上报放弃的任务id列表
     */
    private List<Long> abandonRunningTasks() {
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
        return abandonedTaskIds;
    }

    /**
     * 请求中断一个由本 worker 执行或即将执行的任务。
     *
     * <p>已开始执行的任务会立即触发中断标记与中断回调；已认领但尚未开始执行的任务在开始执行时生效。
     * 本方法幂等，任务已结束时调用不会产生副作用。</p>
     *
     * @param taskId 任务id
     * @return 已受理返回 true；{@code taskId} 为空返回 false
     */
    public boolean interrupt(Long taskId) {
        return registry.interrupt(taskId);
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
     * 任务认领循环。
     */
    private void claimLoop() {
        log.info("{}认领线程已启动", LOG_PREFIX);
        if (!awaitSelfVisible()) {
            log.info("{}认领线程已退出", LOG_PREFIX);
            return;
        }
        while (running) {
            try {
                if (!registry.hasHandlers()) {
                    sleepQuietly();
                    continue;
                }
                // 满载（运行中 + 正在认领的任务数已达上限）时只认领紧急任务，且不占用并发槽位
                boolean hasCapacity = registry.hasCapacity();
                boolean slotAcquired = hasCapacity && registry.tryAcquireSlot();
                if (hasCapacity && !slotAcquired) {
                    // 竞争失败（另一轮认领刚占满容量），本轮按满载处理
                    hasCapacity = false;
                }
                boolean urgentOnly = !hasCapacity;
                int waitSeconds = urgentOnly
                        ? Math.clamp(properties.getClaimWaitSeconds(), 0, MAX_URGENT_CLAIM_WAIT_SECONDS)
                        : properties.getClaimWaitSeconds();
                boolean slotReleased = false;
                try {
                    TaskDispatchInfo dispatchInfo = client.claim(new TaskClaimParam(
                            identity.getServiceId(), identity.getWorkerId(), resolveRole(),
                            new ArrayList<>(registry.getTaskTypes()), urgentOnly, waitSeconds));
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
                    // 登记完成后把认领占用的容量占位转为「运行中」计数（紧急任务未占用槽位，无需释放）
                    SfcTaskExecution execution = new SfcTaskExecution(dispatchInfo, identity.getWorkerId());
                    registry.registerExecution(execution);
                    if (slotAcquired) {
                        registry.releaseSlot();
                        slotReleased = true;
                    }
                    // 登记后再次确认运行状态：停机流程可能刚完成清扫，此时不能再提交执行
                    if (!running) {
                        registry.unregisterExecution(dispatchInfo.id());
                        abandonTask(dispatchInfo, "执行者停机，放弃任务执行权");
                        continue;
                    }
                    try {
                        executor.execute(() -> runner.run(execution, handler));
                    } catch (RuntimeException e) {
                        registry.unregisterExecution(dispatchInfo.id());
                        log.warn("{}任务[{}]提交执行线程池失败，已上报放弃: {}",
                                LOG_PREFIX, dispatchInfo.id(), describe(e));
                        abandonTask(dispatchInfo, "执行线程池拒绝任务，放弃任务执行权");
                    }
                } finally {
                    if (slotAcquired && !slotReleased) {
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
     * 等待本实例出现在服务注册中心，最多等待 {@value #REGISTRATION_CHECK_TIMEOUT_MILLIS} 毫秒。
     *
     * <p>启动自检已保证存在服务发现；等待超时后记录 ERROR 并停止认领，应用继续运行。</p>
     *
     * @return 可以开始认领返回 true；未启用服务发现（防御分支）、超时或停机返回 false
     */
    private boolean awaitSelfVisible() {
        DiscoveryClient discoveryClient = this.discoveryClient;
        if (discoveryClient == null) {
            // 启动自检已拦截该情况，此处仅作防御：无服务发现时无法确认实例可见性，不认领任务
            log.error("{}未启用服务发现，无法自检本实例是否已在注册中心可见，停止认领任务", LOG_PREFIX);
            return false;
        }
        long deadline = System.currentTimeMillis() + REGISTRATION_CHECK_TIMEOUT_MILLIS;
        int attempts = 0;
        boolean pendingLogged = false;
        while (running) {
            attempts++;
            if (identity.isRegistered() && !identity.hasRegisteredInstance()) {
                // 注册中心的实例端口要等 Web 容器绑定完成后回填，此时还不能把退化标识固定下来
                if (!pendingLogged) {
                    log.info("{}服务注册信息尚未完整（实例端口可能还没回填），等待注册完成后再开始认领", LOG_PREFIX);
                    pendingLogged = true;
                }
            } else {
                String serviceId = identity.getServiceId();
                String instanceId = identity.getWorkerId();
                if (isSelfVisible(discoveryClient, serviceId, instanceId)) {
                    log.info("{}身份确认且已在服务注册中心可见：serviceId={}, workerId={}（第 {} 次检查），开始认领任务",
                            LOG_PREFIX, serviceId, instanceId, attempts);
                    return true;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                //noinspection BusyWait
                Thread.sleep(REGISTRATION_CHECK_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        if (!running || Thread.currentThread().isInterrupted()) {
            return false;
        }
        log.error("{}等待 {} 秒后仍未在服务注册中心[{}]中发现本实例[{}]，已停止认领任务（应用继续运行）："
                        + "core 以注册中心中的实例是否存在判定执行者存活，实例不可见时认领的任务会被误判失联并回收，"
                        + "可能造成重复执行；请检查服务注册（discovery/registry）配置",
                LOG_PREFIX, REGISTRATION_CHECK_TIMEOUT_MILLIS / 1000,
                identity.getServiceId(), identity.getWorkerId());
        return false;
    }

    /**
     * 判断本实例是否已出现在服务注册中心。
     *
     * @param discoveryClient 服务发现客户端
     * @param serviceId       本实例所属服务名
     * @param instanceId      本实例标识
     * @return 已在注册中心可见时返回 true
     */
    private boolean isSelfVisible(DiscoveryClient discoveryClient, String serviceId, String instanceId) {
        List<ServiceInstance> instances;
        try {
            instances = discoveryClient.getInstances(serviceId);
        } catch (Throwable e) {
            log.warn("{}查询服务注册中心[{}]失败，{}ms 后重试: {}",
                    LOG_PREFIX, serviceId, REGISTRATION_CHECK_INTERVAL_MILLIS, describe(e));
            return false;
        }
        if (instances.isEmpty()) {
            return false;
        }
        String selfHost = identity.getHost();
        int selfPort = identity.getPort();
        for (ServiceInstance instance : instances) {
            if (instance == null) {
                continue;
            }
            if (instanceId.equals(instance.getInstanceId())) {
                return true;
            }
            if (selfHost != null && selfPort > 0
                    && selfHost.equals(instance.getHost()) && selfPort == instance.getPort()) {
                log.debug("{}注册中心中的实例 id[{}] 与本地解析结果[{}] 不一致，按 host:port 判定为同一实例",
                        LOG_PREFIX, instance.getInstanceId(), instanceId);
                return true;
            }
        }
        return false;
    }

    /**
     * 上报放弃一个已认领但无法执行的任务，任务会被 core 重置回队列。
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
     * 解析本实例的执行者角色：取配置项 {@code sfc.task.worker.role}，空白时按微服务实例
     * （{@code SERVICE}）处理。
     *
     * @return 执行者角色
     */
    private String resolveRole() {
        String role = properties.getRole();
        return role == null || role.isBlank() ? DEFAULT_WORKER_ROLE : role.trim();
    }

    /**
     * 驱动一次进度/日志上报；异常不外抛。
     */
    private void tickQuietly() {
        try {
            reporter.tick();
        } catch (Throwable e) {
            log.warn("{}周期上报异常: {}", LOG_PREFIX, describe(e));
        }
    }

    /**
     * 等待线程结束，忽略中断。
     *
     * @param thread 目标线程
     */
    private static void joinQuietly(Thread thread) {
        try {
            thread.join(CLAIM_THREAD_JOIN_MILLIS);
            if (thread.isAlive()) {
                log.warn("{}认领线程在 {}ms 内未退出，继续停机流程；其遗留任务由调度中心的故障转移/启动恢复回收",
                        LOG_PREFIX, CLAIM_THREAD_JOIN_MILLIS);
            }
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
