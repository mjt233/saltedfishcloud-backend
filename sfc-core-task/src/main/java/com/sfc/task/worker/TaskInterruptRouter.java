package com.sfc.task.worker;

import com.sfc.cloud.task.worker.SfcTaskWorkerClient;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.report.TaskReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 任务中断路由器。
 *
 * <p>core 节点内嵌执行者与微服务实例共用同一套 Worker 协议，中断指令统一下发：按任务记录中的
 * 执行者所属服务名（{@code claimedService}）查询服务注册中心，按 {@code host:port}
 * （{@code claimedWorker}）匹配到执行者实例后，调用执行者的内部中断接口。</p>
 *
 * <p>执行者已不在服务注册中心时直接取消任务，避免僵尸任务；服务发现查询失败或中断指令下发失败时
 * 不贸然取消任务，由调用方决定是否重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskInterruptRouter {
    private static final String LOG_PREFIX = "[异步任务中断]";

    private final TaskReportService reportService;

    private final DiscoveryClient discoveryClient;

    /**
     * Worker 协议客户端：同时用于向执行者下发中断指令。
     * <p>{@code sfc.task.worker.enabled=false} 时 SDK 不会装配该 Bean，此时中断下发失败但应用仍可启动。</p>
     */
    private final ObjectProvider<SfcTaskWorkerClient> workerClientProvider;

    /**
     * 中断一个运行中的任务。
     *
     * @param record 任务记录
     * @return 中断指令已成功下发返回 true
     */
    public boolean interrupt(AsyncTaskRecord record) {
        if (record == null || record.getId() == null) {
            return false;
        }
        String workerId = record.getClaimedWorker();
        if (!StringUtils.hasText(workerId)) {
            return reportService.cancelRunningTask(record.getId(), "任务未记录执行者");
        }
        String serviceId = record.getClaimedService();
        if (!StringUtils.hasText(serviceId)) {
            return reportService.cancelRunningTask(record.getId(), "任务未记录执行者所属服务");
        }
        List<ServiceInstance> instances;
        try {
            instances = discoveryClient.getInstances(serviceId);
        } catch (Throwable e) {
            // 服务发现查询失败不能贸然取消任务，交由调用方提示重试
            log.warn("{}查询服务[{}]实例失败，无法下发任务 {} 的中断指令: {}",
                    LOG_PREFIX, serviceId, record.getId(), e.getMessage());
            return false;
        }
        ServiceInstance instance = instances.stream()
                .filter(item -> item != null && workerId.equals(item.getHost() + ":" + item.getPort()))
                .findFirst()
                .orElse(null);
        if (instance == null) {
            return reportService.cancelRunningTask(record.getId(),
                    "执行者 " + serviceId + "/" + workerId + " 已不在服务注册中心");
        }
        SfcTaskWorkerClient workerClient = workerClientProvider.getIfAvailable();
        if (workerClient == null) {
            log.warn("{}未找到 Worker 协议客户端（sfc.task.worker.enabled=false？），无法向执行者下发任务 {} 的中断指令",
                    LOG_PREFIX, record.getId());
            return false;
        }
        // TODO 后续考虑把 core → worker 的中断回调迁移到 core 内部实现（见 SfcTaskWorkerClient#interruptWorker 上的 TODO），
        //      届时不再经由 sfc-task-worker SDK 的公开方法下发。
        boolean success = workerClient.interruptWorker(buildBaseUrl(instance), record.getId());
        if (!success) {
            log.warn("{}向worker[{}]下发任务 {}-{} 中断指令失败，任务将保持运行",
                    LOG_PREFIX, workerId, record.getName(), record.getId());
        }
        return success;
    }

    /**
     * 由服务实例构造执行者的内部接口基础地址。
     *
     * @param instance 服务实例
     * @return 形如 {@code http://host:port} 的基础地址
     */
    private static String buildBaseUrl(ServiceInstance instance) {
        String scheme = instance.isSecure() ? "https" : "http";
        return scheme + "://" + instance.getHost() + ":" + instance.getPort();
    }
}
