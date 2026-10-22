package com.sfc.task.worker;

import com.sfc.task.AsyncTaskExecutor;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.schedule.TaskClaimService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * core 本地任务执行 worker。
 * <p>core 节点本身也是异步任务系统的执行者：启动时向 {@link TaskWorkerRegistryService} 注册本地
 * 支持的任务类型（即本节点注册的任务工厂类型），然后按类型认领任务并交给本地任务执行器执行。</p>
 *
 * <p>与旧机制的关键差异：不再「抓取任意任务后检查是否支持」，而是只认领本节点声明支持的任务类型，
 * 从根本上消除了任务在节点间反复回退的问题。</p>
 */
@Slf4j
@Component
public class LocalTaskWorker {
    private static final String LOG_PREFIX = "[本地异步任务Worker]";

    /**
     * 单次认领的最长等待秒数
     */
    private static final int CLAIM_WAIT_SECONDS = 10;

    private final AsyncTaskExecutor executor;

    private final TaskClaimService claimService;

    private final TaskWorkerRegistryService workerRegistryService;

    private volatile boolean running = false;

    private Thread workerThread;

    public LocalTaskWorker(AsyncTaskExecutor executor,
                           TaskClaimService claimService,
                           TaskWorkerRegistryService workerRegistryService) {
        this.executor = executor;
        this.claimService = claimService;
        this.workerRegistryService = workerRegistryService;
    }

    /**
     * 应用就绪后启动本地 worker。
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        try {
            registerSelf();
        } catch (Throwable e) {
            // 注册失败不阻断应用启动（缓存/Redis 可能暂不可用），由心跳自动重试
            log.error("{}启动时注册失败，将依赖后续心跳重试", LOG_PREFIX, e);
        }
        workerThread = new Thread(this::claimLoop, "SfcAsyncTask-LocalWorker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("{}启动完成，workerId: {}", LOG_PREFIX, workerRegistryService.getLocalWorkerId());
    }

    /**
     * 容器关闭时停止认领并注销本地 worker。<br>
     * 优先级最高，确保在任务管理器回收任务之前停止认领，避免自己回收的任务被自己再次认领。
     */
    @EventListener(ContextClosedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        workerRegistryService.unregisterLocal();
        log.info("{}已停止", LOG_PREFIX);
    }

    /**
     * 周期性心跳：刷新本地 worker 的注册信息（存活状态、当前负载与支持的任务类型）。
     */
    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void heartbeat() {
        if (!running) {
            return;
        }
        try {
            registerSelf();
        } catch (Exception e) {
            log.error("{}心跳失败", LOG_PREFIX, e);
        }
    }

    /**
     * 认领循环：只认领本地支持的任务类型。
     */
    private void claimLoop() {
        while (running) {
            try {
                if (!executor.isRunning()) {
                    sleepQuietly(500);
                    continue;
                }
                Collection<String> types = executor.getRegisteredTaskTypes();
                if (types.isEmpty()) {
                    sleepQuietly(1000);
                    continue;
                }
                if (executor.getCurrentLoad() >= executor.getMaxLoad()) {
                    sleepQuietly(1000);
                    continue;
                }
                AsyncTaskRecord record = claimService.claim(
                        workerRegistryService.getLocalWorkerId(), types, CLAIM_WAIT_SECONDS);
                if (record == null || !running) {
                    continue;
                }
                executor.executeClaimed(record);
            } catch (Throwable e) {
                log.error("{}认领或执行任务出错", LOG_PREFIX, e);
                sleepQuietly(1000);
            }
        }
        log.info("{}认领线程退出", LOG_PREFIX);
    }

    /**
     * 注册或续约本地 worker 信息。
     */
    private void registerSelf() {
        workerRegistryService.registerLocal(
                executor.getRegisteredTaskTypes(),
                executor.getMaxLoad(),
                executor.getCurrentLoad()
        );
    }

    /**
     * 静默休眠，被中断时恢复中断标记。
     *
     * @param ms 休眠毫秒数
     */
    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
