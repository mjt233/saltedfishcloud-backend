package com.sfc.task.worker;

import com.sfc.rpc.annotation.RPCResource;
import com.sfc.rpc.exception.RPCIgnoreException;
import com.sfc.task.AsyncTaskExecutor;
import com.sfc.task.DefaultAsyncTaskRpcService;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.report.TaskReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 任务中断路由器。
 * <p>任务可能由当前 core 节点、其他 core 节点或微服务实例执行，中断请求需要按任务的认领者
 * 路由到正确的执行者：</p>
 * <ul>
 *     <li>当前节点执行的本地任务：直接调用本地执行器中断；</li>
 *     <li>其他 core 节点执行的任务：通过集群 RPC 广播中断（持有该任务的节点会响应）；</li>
 *     <li>微服务实例执行的任务：通过 HTTP 回调 worker 的中断接口。</li>
 * </ul>
 * <p>若认领者已失联（例如执行者事故退出），则直接取消任务，避免出现永远无法中断的「僵尸任务」。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskInterruptRouter {
    private static final String LOG_PREFIX = "[异步任务中断]";

    private final AsyncTaskExecutor executor;

    private final TaskWorkerRegistryService workerRegistryService;

    private final TaskWorkerCallbackClient callbackClient;

    private final TaskReportService reportService;

    @RPCResource
    private DefaultAsyncTaskRpcService asyncTaskRpcService;

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

        // 当前节点执行的本地任务：直接中断
        if (workerId.equals(workerRegistryService.getLocalWorkerId())) {
            return executor.interrupt(record.getId());
        }

        TaskWorkerInfo worker = workerRegistryService.get(workerId);
        if (worker == null) {
            // 执行者已失联，直接取消任务
            return reportService.cancelRunningTask(record.getId(), "执行者 " + workerId + " 已失联");
        }

        if (worker.getKind() == TaskWorkerKind.LOCAL_NODE) {
            // 其他 core 节点执行的任务：集群 RPC 广播中断
            try {
                asyncTaskRpcService.interrupt(record.getId());
                return true;
            } catch (RPCIgnoreException e) {
                // 集群内没有任何节点持有该任务（执行者信息与实际不符），直接取消避免僵尸任务
                return reportService.cancelRunningTask(record.getId(), "集群内未找到任务执行节点");
            } catch (Throwable e) {
                // 网络/超时等临时故障：不能贸然取消任务，交由调用方提示重试
                log.warn("{}通过集群RPC中断任务 {}-{} 失败: {}",
                        LOG_PREFIX, record.getName(), record.getId(), e.getMessage());
                return false;
            }
        }

        // 微服务实例执行的任务：HTTP 回调
        boolean success = callbackClient.requestInterrupt(worker, record.getId());
        if (!success) {
            log.warn("{}向worker[{}]下发任务 {}-{} 中断指令失败，任务将保持运行",
                    LOG_PREFIX, workerId, record.getName(), record.getId());
        }
        return success;
    }
}
