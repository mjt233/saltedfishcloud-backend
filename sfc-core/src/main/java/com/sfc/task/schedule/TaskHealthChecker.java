package com.sfc.task.schedule;

import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.report.TaskReportService;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.worker.TaskWorkerInfo;
import com.sfc.task.worker.TaskWorkerRegistryService;
import com.xiaotao.saltedfishcloud.annotations.ClusterScheduleJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务健康检查与故障转移。
 * <p>周期性检查所有运行中任务的执行者是否仍然存活：执行者注册信息过期（心跳中断）即视为失联，
 * 其名下任务会被保存日志、重置为等待中并重新发布，交由其他具备能力的执行者接管；达到最大重试
 * 次数的任务会被置为失败。</p>
 *
 * <p>同时会检查「等待中但没有任何存活执行者支持的任务类型」并输出告警，便于运维发现任务
 * 长时间排队的原因（例如对应的微服务实例没有启动）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskHealthChecker {
    private static final String LOG_PREFIX = "[异步任务健康检查]";

    private final AsyncTaskRecordRepo taskRecordRepo;

    private final TaskWorkerRegistryService workerRegistryService;

    private final TaskReportService reportService;

    /**
     * 执行任务健康检查与故障转移。
     */
    @Async
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    @ClusterScheduleJob(value = "async_task_health_check", describe = "异步任务执行者健康检查与故障转移")
    public void check() {
        try {
            checkRunningTasks();
            checkWaitingTaskTypes();
        } catch (Throwable e) {
            log.error("{}执行失败", LOG_PREFIX, e);
        }
    }

    /**
     * 回收执行者已失联的运行中任务。
     */
    private void checkRunningTasks() {
        List<AsyncTaskRecord> runningTasks = taskRecordRepo.listRunningTask();
        if (runningTasks == null || runningTasks.isEmpty()) {
            return;
        }
        Map<String, List<AsyncTaskRecord>> deadWorkerTasks = new HashMap<>();
        for (AsyncTaskRecord record : runningTasks) {
            String workerId = record.getClaimedWorker();
            if (workerId == null || !workerRegistryService.isAlive(workerId)) {
                deadWorkerTasks.computeIfAbsent(
                        workerId == null ? "<unknown>" : workerId,
                        ignored -> new ArrayList<>()
                ).add(record);
            }
        }
        deadWorkerTasks.forEach((workerId, records) ->
                reportService.reclaimDeadWorkerTasks(records, "任务执行者 " + workerId + " 已失联，任务被回收重试"));
    }

    /**
     * 检查等待中的任务类型是否有存活执行者支持，仅输出告警不修改任务状态。
     */
    private void checkWaitingTaskTypes() {
        List<String> waitingTypes = taskRecordRepo.listWaitingTaskTypes();
        if (waitingTypes == null || waitingTypes.isEmpty()) {
            return;
        }
        Set<String> aliveTypes = new HashSet<>();
        for (TaskWorkerInfo worker : workerRegistryService.list()) {
            if (worker.getTypes() != null) {
                aliveTypes.addAll(worker.getTypes());
            }
        }
        for (String taskType : waitingTypes) {
            if (taskType != null && !aliveTypes.contains(taskType)) {
                log.warn("{}任务类型[{}]当前没有任何存活的执行者支持，相关任务将持续排队等待",
                        LOG_PREFIX, taskType);
            }
        }
    }
}
