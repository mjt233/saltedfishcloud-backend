package com.sfc.task.controller;

import com.sfc.cloud.task.worker.SfcTaskWorkerIdentity;
import com.sfc.common.model.SfcPageInfo;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.task.AsyncTaskFactoryRegistry;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.AsyncTaskRecordService;
import com.sfc.task.worker.TaskWorkerInfo;
import com.sfc.task.model.AsyncTaskQueryParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.prog.ProgressRecordVO;
import com.sfc.task.report.TaskReportService;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import jakarta.annotation.security.RolesAllowed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 异步任务管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/asyncTask")
public class AsyncTaskController {
    @Autowired
    private AsyncTaskManager asyncTaskManager;

    @Autowired
    private AsyncTaskRecordRepo asyncTaskRecordRepo;

    @Autowired
    private AsyncTaskRecordService asyncTaskRecordService;

    @Autowired
    private TaskReportService taskReportService;

    @Autowired
    private DiscoveryClient discoveryClient;

    /**
     * 当前节点的执行者身份；未启用任务 Worker 时为空，此时管理接口不会把任何实例标记为本地。
     */
    @Autowired
    private ObjectProvider<SfcTaskWorkerIdentity> workerIdentityProvider;

    @Autowired
    private AsyncTaskFactoryRegistry asyncTaskFactoryRegistry;

    /**
     * 获取服务注册中心中可参与任务执行的服务实例列表（仅管理员）；当前 core 节点自身会额外
     * 给出其支持的任务类型。
     *
     * @return 执行者实例列表
     */
    @RolesAllowed({"ADMIN"})
    @GetMapping("listWorker")
    public SfcJsonResult<List<TaskWorkerInfo>> listWorker() {
        return SfcJsonResult.getInstance(listWorkerInstances());
    }

    /**
     * 从服务注册中心组装执行者实例列表。
     *
     * @return 实例列表，查询失败时返回空列表
     */
    private List<TaskWorkerInfo> listWorkerInstances() {
        List<TaskWorkerInfo> result = new ArrayList<>();
        SfcTaskWorkerIdentity localIdentity = workerIdentityProvider.getIfAvailable();
        try {
            List<String> services = discoveryClient.getServices();
            if (services.isEmpty()) {
                return result;
            }
            for (String serviceId : services) {
                if (serviceId == null) {
                    continue;
                }
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
                for (ServiceInstance instance : instances) {
                    String workerId = instance.getHost() + ":" + instance.getPort();
                    boolean local = localIdentity != null && localIdentity.getWorkerId().equals(workerId);
                    result.add(TaskWorkerInfo.builder()
                            .serviceId(instance.getServiceId())
                            .workerId(workerId)
                            .role(local ? com.sfc.task.AsyncTaskConstants.WorkerRole.CORE : null)
                            .host(instance.getHost())
                            .port(instance.getPort())
                            .secure(instance.isSecure())
                            .local(local)
                            .types(local ? new ArrayList<>(asyncTaskFactoryRegistry.getTaskTypes())
                                    : Collections.emptyList())
                            .build());
                }
            }
        } catch (Throwable e) {
            log.warn("[异步任务管理]组装执行者列表失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 获取任务执行进度信息
     */
    @PostMapping("getProg")
    public SfcJsonResult<List<ProgressRecordVO>> getProg(@RequestBody List<Long> taskId) {
        return SfcJsonResult.getInstance(taskId.stream().map(id -> {
            ProgressRecord record = taskReportService.getProgress(id);
            if (record == null) {
                return null;
            }
            return ProgressRecordVO.builder()
                    .taskId(id.toString())
                    .record(record)
                    .build();
        }).filter(Objects::nonNull).toList());
    }

    @GetMapping("listRecord")
    public SfcJsonResult<SfcPageInfo<AsyncTaskRecord>> listRecord(AsyncTaskQueryParam param) {
        return SfcJsonResult.getInstance(asyncTaskRecordService.listRecord(param));
    }

    @GetMapping("getById")
    public SfcJsonResult<AsyncTaskRecord> getById(@RequestParam("taskId") Long taskId) {
        return SfcJsonResult.getInstance(asyncTaskRecordRepo.getReferenceById(taskId));
    }

    /**
     * 阻塞等待执行完成。
     * @param taskId        等待的任务id
     * @param timeout       最长等待时长
     * @return  是否执行完成
     */
    @GetMapping("waitTaskExit")
    public SfcJsonResult<Boolean> waitTaskExit(@RequestParam("taskId") Long taskId,
                                        @RequestParam("timeout") Long timeout) throws IOException {
        try {
            asyncTaskManager.waitTaskExit(taskId, timeout, TimeUnit.SECONDS);
            return SfcJsonResult.getInstance(true);
        } catch (InterruptedException e) {
            return SfcJsonResult.getInstance(false);
        }
    }

    /**
     * 重新运行任务
     * @param taskId    任务id
     * @return          新任务对象
     */
    @PostMapping("rerun")
    public SfcJsonResult<AsyncTaskRecord> rerun(@RequestParam("taskId") Long taskId) throws IOException {
        return SfcJsonResult.getInstance(asyncTaskManager.rerun(taskId));
    }

    @RequestMapping("/interrupt")
    public SfcJsonResult<Object> interrupt(@RequestParam("taskId") Long taskId) throws IOException {
        AsyncTaskRecord record = asyncTaskRecordRepo.getReferenceById(taskId);
        SfcUIDValidator.validate(record.getUid(), true);
        asyncTaskManager.interrupt(taskId);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 获取任务日志
     * @param taskId  任务id
     * @return         日志正文
     */
    @GetMapping("getLog")
    public SfcJsonResult<String> getLog(@RequestParam("taskId") Long taskId) throws IOException {
        return SfcJsonResult.getInstance(ResourceUtils.resourceToString(asyncTaskManager.getTaskLog(taskId, true)));
    }
}
