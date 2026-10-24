package com.sfc.task.worker;

import com.sfc.cloud.task.worker.SfcTaskWorkerIdentity;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.report.TaskReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本节点启动恢复：回收本执行者上次退出时遗留的运行中任务。
 *
 * <p>执行者身份由服务发现实例地址（{@code host:port}）确定，同端口重启后身份与上次进程一致；
 * 若不主动回收，健康检查会认为执行者仍然存活，任务将一直停留在运行中。本组件在 Web 容器就绪后、
 * Worker 开始认领任务前执行一次，把 {@code claimedService} 与 {@code claimedWorker} 都指向本实例的
 * 运行中任务按故障转移规则回收：普通任务重新排队，临时任务置为失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupTaskRecovery implements SmartLifecycle {
    private static final String LOG_PREFIX = "[异步任务启动恢复]";

    /**
     * 执行阶段：晚于 Web 容器（{@code Integer.MAX_VALUE - 2048}）、早于 Worker 运行时（默认 {@code Integer.MAX_VALUE}）。
     * <p>此时实例端口已由注册中心回填，且 Worker 尚未开始认领任务。</p>
     */
    private static final int PHASE = Integer.MAX_VALUE - 1;

    private final ObjectProvider<SfcTaskWorkerIdentity> workerIdentityProvider;

    private final AsyncTaskRecordRepo taskRecordRepo;

    private final TaskReportService reportService;

    private volatile boolean running;

    /**
     * 回收本实例上次退出时遗留的运行中任务。
     */
    @Override
    public void start() {
        running = true;
        SfcTaskWorkerIdentity identity = workerIdentityProvider.getIfAvailable();
        if (identity == null) {
            log.debug("{}未启用任务执行者，跳过启动恢复", LOG_PREFIX);
            return;
        }
        String serviceId = identity.getServiceId();
        String workerId = identity.getWorkerId();
        try {
            List<AsyncTaskRecord> staleTasks = taskRecordRepo.listRunningTask().stream()
                    .filter(record -> serviceId.equals(record.getClaimedService())
                            && workerId.equals(record.getClaimedWorker()))
                    .toList();
            if (staleTasks.isEmpty()) {
                return;
            }
            log.warn("{}发现 {} 个本实例上次退出时遗留的运行中任务，开始回收: {}", LOG_PREFIX, staleTasks.size(),
                    staleTasks.stream().map(AsyncTaskRecord::getId).toList());
            reportService.reclaimDeadWorkerTasks(staleTasks, "任务执行者上次退出时未完成执行，任务被回收");
        } catch (Throwable e) {
            log.error("{}回收遗留运行中任务失败", LOG_PREFIX, e);
        }
    }

    /**
     * 停止组件（仅重置运行标记，无资源需要释放）。
     */
    @Override
    public void stop() {
        running = false;
    }

    /**
     * 是否处于运行中。
     *
     * @return 启动后返回 true
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 生命周期执行阶段。
     *
     * @return 本组件阶段
     */
    @Override
    public int getPhase() {
        return PHASE;
    }
}
