package com.sfc.task.schedule;

import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.constants.TaskMQTopic;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 任务认领服务。
 *
 * <p>执行者按自己支持的任务类型认领等待中的任务；同一任务并发认领时只有一个执行者能成功。
 * 支持普通认领与「只认领紧急任务」两种模式；无任务可认领时以长轮询等待，新任务发布时立即唤醒。</p>
 */
@Slf4j
@Component
public class TaskClaimService implements InitializingBean {
    private static final String LOG_PREFIX = "[异步任务认领]";

    private final AsyncTaskRecordRepo taskRecordRepo;

    private final MQService mqService;

    /**
     * 长轮询等待监视器
     */
    private final Object monitor = new Object();

    private long subscribeId;

    public TaskClaimService(AsyncTaskRecordRepo taskRecordRepo, MQService mqService) {
        this.taskRecordRepo = taskRecordRepo;
        this.mqService = mqService;
    }

    @Override
    public void afterPropertiesSet() {
        // 订阅新任务发布广播，用于唤醒所有正在等待任务的执行者。
        // 订阅失败不阻断启动：认领会退化为按等待时长轮询，功能仍可用只是唤醒不及时。
        try {
            this.subscribeId = mqService.subscribeBroadcast(TaskMQTopic.TASK_PUBLISH, ignored -> wakeupAll());
        } catch (Throwable e) {
            log.error("{}订阅新任务发布广播失败，任务认领将退化为定时轮询", LOG_PREFIX, e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (subscribeId > 0) {
            try {
                mqService.unsubscribe(subscribeId);
            } catch (Throwable ignored) {
                // 停机阶段消息队列可能已不可用，忽略
            }
        }
    }

    /**
     * 认领一个等待中的任务。
     *
     * @param serviceId   执行者所属服务名（服务发现中的 serviceId）
     * @param workerId    执行者实例地址（{@code host:port}）
     * @param role        执行者角色，取值见 {@link AsyncTaskConstants.WorkerRole}
     * @param types       本次允许认领的任务类型集合，为空时直接返回 null
     * @param urgentOnly  是否只认领紧急任务（执行者满载时为 true）
     * @param waitSeconds 无任务可认领时的最长等待秒数，小于等于 0 表示不等待
     * @return 认领成功的任务记录，没有可认领任务时返回 null
     */
    public AsyncTaskRecord claim(String serviceId, String workerId, String role, Collection<String> types,
                                 boolean urgentOnly, int waitSeconds) {
        if (workerId == null || types == null || types.isEmpty()) {
            return null;
        }
        long deadline = System.currentTimeMillis() + Math.max(waitSeconds, 0) * 1000L;
        while (true) {
            AsyncTaskRecord record = tryClaim(serviceId, workerId, role, types, urgentOnly);
            if (record != null) {
                return record;
            }
            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0) {
                return null;
            }
            synchronized (monitor) {
                try {
                    monitor.wait(remain);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }

    /**
     * 尝试认领一次任务，不做等待。
     *
     * @param serviceId  执行者所属服务名
     * @param workerId   执行者标识
     * @param role       执行者角色
     * @param types      允许认领的任务类型集合
     * @param urgentOnly 是否只认领紧急任务
     * @return 认领成功的任务记录，没有可认领任务时返回 null
     */
    protected AsyncTaskRecord tryClaim(String serviceId, String workerId, String role,
                                       Collection<String> types, boolean urgentOnly) {
        List<AsyncTaskRecord> candidates;
        try {
            candidates = urgentOnly
                    ? taskRecordRepo.findUrgentWaitingByTaskTypes(
                            types, AsyncTaskConstants.Priority.URGENT, PageRequest.of(0, 1))
                    : taskRecordRepo.findWaitingByTaskTypes(types, PageRequest.of(0, 1));
        } catch (Exception e) {
            log.error("{}查询等待任务失败", LOG_PREFIX, e);
            return null;
        }
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        AsyncTaskRecord record = candidates.getFirst();
        Date now = new Date();
        int updated;
        try {
            updated = taskRecordRepo.claim(record.getId(), serviceId, workerId, role, now);
        } catch (Exception e) {
            log.error("{}认领任务失败: {}", LOG_PREFIX, record.getId(), e);
            return null;
        }
        if (updated <= 0) {
            // 并发认领竞争失败，交由外层循环重新查询
            return null;
        }
        record.setStatus(AsyncTaskConstants.Status.RUNNING);
        record.setClaimedService(serviceId);
        record.setClaimedWorker(workerId);
        record.setClaimedRole(role);
        record.setClaimedAt(now);
        record.setLastReportAt(now);
        log.info("{}worker[{}/{}] 认领任务 {}-{}", LOG_PREFIX, serviceId, workerId, record.getName(), record.getId());
        return record;
    }

    /**
     * 唤醒所有正在长轮询等待任务的执行者。
     */
    public void wakeupAll() {
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }
}
