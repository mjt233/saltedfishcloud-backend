package com.sfc.task.schedule;

import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.report.TaskReportService;
import com.xiaotao.saltedfishcloud.annotations.ClusterScheduleJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 任务健康检查与故障转移。
 *
 * <p>周期检查运行中任务的执行者实例是否仍在服务注册中心：实例不存在即视为失联，
 * 其名下任务被回收（普通任务重排重试，临时任务置为失败）；同时告警长时间无人认领的等待任务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskHealthChecker {
    private static final String LOG_PREFIX = "[异步任务健康检查]";

    /**
     * 等待任务告警阈值（分钟）：等待超过该时长的任务会被统计并告警
     */
    private static final long WAITING_WARN_MINUTES = 5;

    /**
     * 等待任务告警的最小输出间隔（毫秒），避免每轮检查都刷屏
     */
    private static final long WAITING_WARN_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final AsyncTaskRecordRepo taskRecordRepo;

    private final TaskReportService reportService;

    private final DiscoveryClient discoveryClient;

    /**
     * 上次输出等待任务告警的时间
     */
    private volatile long lastWaitingWarnAt = 0L;

    /**
     * 执行任务健康检查与故障转移。
     */
    @Async
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    @ClusterScheduleJob(value = "async_task_health_check", describe = "异步任务执行者健康检查与故障转移")
    public void check() {
        try {
            checkRunningTasks();
            checkLongWaitingTasks();
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
        // serviceId -> 当前注册中心中的实例地址集合（host:port）；值为 null 表示查询失败（本轮跳过该服务的判定）
        Map<String, Set<String>> instancesCache = new LinkedHashMap<>();
        Map<String, List<AsyncTaskRecord>> deadWorkerTasks = new LinkedHashMap<>();
        for (AsyncTaskRecord record : runningTasks) {
            String serviceId = record.getClaimedService();
            String workerId = record.getClaimedWorker();
            if (!StringUtils.hasText(serviceId) || !StringUtils.hasText(workerId)) {
                deadWorkerTasks.computeIfAbsent("<unknown>", ignored -> new ArrayList<>()).add(record);
                continue;
            }
            Set<String> instanceAddresses;
            if (instancesCache.containsKey(serviceId)) {
                instanceAddresses = instancesCache.get(serviceId);
            } else {
                instanceAddresses = listInstanceAddresses(serviceId);
                instancesCache.put(serviceId, instanceAddresses);
            }
            if (instanceAddresses == null) {
                // 服务发现查询失败，本轮不判定该服务，避免误回收
                continue;
            }
            if (!instanceAddresses.contains(workerId)) {
                deadWorkerTasks.computeIfAbsent(serviceId, ignored -> new ArrayList<>()).add(record);
            }
        }
        deadWorkerTasks.forEach((serviceId, records) ->
                reportService.reclaimDeadWorkerTasks(records,
                        "任务执行者 " + serviceId + " 的实例已不在服务注册中心，任务被回收"));
    }

    /**
     * 查询指定服务在注册中心中的全部实例地址（host:port）。
     *
     * @param serviceId 服务名
     * @return 实例地址集合；查询失败时返回 null
     */
    private Set<String> listInstanceAddresses(String serviceId) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            Set<String> addresses = new HashSet<>();
            if (instances != null) {
                for (ServiceInstance instance : instances) {
                    addresses.add(instance.getHost() + ":" + instance.getPort());
                }
            }
            return addresses;
        } catch (Throwable e) {
            log.warn("{}查询服务[{}]实例失败，本轮跳过该服务的失联判定: {}", LOG_PREFIX, serviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 检查长时间无人认领的等待任务并输出告警。
     */
    private void checkLongWaitingTasks() {
        long now = System.currentTimeMillis();
        if (now - lastWaitingWarnAt < WAITING_WARN_INTERVAL_MILLIS) {
            return;
        }
        Date threshold = new Date(now - TimeUnit.MINUTES.toMillis(WAITING_WARN_MINUTES));
        List<Object[]> stats;
        try {
            stats = taskRecordRepo.statWaitingBefore(threshold);
        } catch (Exception e) {
            log.warn("{}统计等待任务失败: {}", LOG_PREFIX, e.getMessage());
            return;
        }
        if (stats == null || stats.isEmpty()) {
            return;
        }
        lastWaitingWarnAt = now;
        long total = 0L;
        StringBuilder detail = new StringBuilder();
        for (Object[] row : stats) {
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            total += count;
            if (detail.length() > 0) {
                detail.append(", ");
            }
            detail.append(row[0]).append(" ×").append(count);
        }
        log.warn("{}存在 {} 个等待超过 {} 分钟仍未被执行者认领的任务（按类型: {}），"
                        + "请检查任务类型是否写错或对应执行者是否已部署",
                LOG_PREFIX, total, WAITING_WARN_MINUTES, detail);
    }
}
