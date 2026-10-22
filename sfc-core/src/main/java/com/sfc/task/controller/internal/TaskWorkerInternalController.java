package com.sfc.task.controller.internal;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskCreateParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.protocol.TaskAbandonParam;
import com.sfc.task.protocol.TaskClaimParam;
import com.sfc.task.protocol.TaskDispatchInfo;
import com.sfc.task.protocol.TaskFinishReportParam;
import com.sfc.task.protocol.TaskLogReportParam;
import com.sfc.task.protocol.TaskProgressReportParam;
import com.sfc.task.protocol.TaskReportStartParam;
import com.sfc.task.protocol.TaskSubmitParam;
import com.sfc.task.protocol.TaskWorkerRegisterParam;
import com.sfc.task.protocol.TaskWorkerUnregisterParam;
import com.sfc.task.report.TaskReportService;
import com.sfc.task.schedule.TaskClaimService;
import com.sfc.task.worker.TaskWorkerKind;
import com.sfc.task.worker.TaskWorkerRegistryService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 异步任务执行者（worker）内部协议控制器。
 *
 * <p>{@value #PREFIX} 属于 {@code /api/internal/**} 前缀，由 {@code sfc-auth} SDK 的
 * {@code SfcInternalAuthFilter} 强制校验服务间调用凭证（请求头 {@code X-Sfc-Internal-Token} 中
 * 每次调用新签发的 HS256 JWT），客户端无法直接访问；网关侧也会直接拒绝该前缀。</p>
 *
 * <p>本控制器是微服务 Worker SDK 与 core 之间的完整协议面：worker 通过它注册能力、认领任务、
 * 上报执行进度与结果，从而让微服务执行的任务与 core 本地任务共享同一套调度、监控与管理能力。</p>
 */
@Slf4j
@RestController
@RequestMapping(TaskWorkerInternalController.PREFIX)
@ProtectBlock
@RequiredArgsConstructor
@Tag(name = "异步任务-服务间内部接口")
public class TaskWorkerInternalController {

    /**
     * 接口前缀
     */
    public static final String PREFIX = "/api/internal/task";

    private final TaskWorkerRegistryService workerRegistryService;

    private final TaskClaimService claimService;

    private final TaskReportService reportService;

    private final AsyncTaskManager asyncTaskManager;

    /**
     * 注册或续约一个任务执行者。<br>
     * 该接口幂等：worker 启动时调用一次完成注册，之后按心跳周期重复调用以续约并刷新负载与能力信息。
     *
     * @param param 注册参数
     * @return 空成功响应
     */
    @PostMapping("worker/register")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Object> register(@RequestBody TaskWorkerRegisterParam param) {
        if (!SfcStringUtils.hasText(param.workerId())) {
            throw new SfcJsonException(400, "workerId 不能为空");
        }
        TaskWorkerKind kind = parseKind(param.kind());
        if (kind == TaskWorkerKind.REMOTE_SERVICE && !SfcStringUtils.hasText(param.callbackUrl())) {
            throw new SfcJsonException(400, "微服务worker必须提供 callbackUrl");
        }
        workerRegistryService.register(
                param.workerId(),
                kind,
                param.serviceName(),
                param.callbackUrl(),
                param.types() == null ? new ArrayList<>() : param.types(),
                param.maxLoad(),
                param.currentLoad()
        );
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 注销一个任务执行者。
     *
     * @param param 注销参数
     * @return 空成功响应
     */
    @PostMapping("worker/unregister")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Object> unregister(@RequestBody TaskWorkerUnregisterParam param) {
        workerRegistryService.unregister(param.workerId());
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 认领一个任务。无任务可认领时按请求的等待时长进行长轮询等待。
     *
     * @param param 认领参数
     * @return 认领到的任务信息，无任务时为 null
     */
    @PostMapping("claim")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<TaskDispatchInfo> claim(@RequestBody TaskClaimParam param) {
        if (!workerRegistryService.isAlive(param.workerId())) {
            throw new SfcJsonException(410, "worker未注册或注册已过期，请重新注册");
        }
        int waitSeconds = param.waitSeconds() == null ? 0 : param.waitSeconds();
        AsyncTaskRecord record = claimService.claim(param.workerId(), param.types(), waitSeconds);
        return SfcJsonResult.getInstance(record == null ? null : TaskDispatchInfo.from(record));
    }

    /**
     * 以指定用户身份提交一个异步任务。供微服务把耗时任务交给统一的异步任务系统调度执行。
     *
     * @param param 提交参数
     * @return 新任务id
     */
    @PostMapping("submit")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Long> submit(@RequestBody TaskSubmitParam param) throws IOException {
        AsyncTaskCreateParam createParam = AsyncTaskCreateParam.builder()
                .name(param.name())
                .taskType(param.taskType())
                .params(param.params())
                .cpuOverhead(param.cpuOverhead())
                .isTemp(param.isTemp())
                .immediate(false)
                .build();
        AsyncTaskRecord record = asyncTaskManager.submitTaskForUser(param.uid(), createParam);
        return SfcJsonResult.getInstance(record.getId());
    }

    /**
     * 上报任务开始执行。
     *
     * @param param 上报参数
     * @return 上报是否被接受（任务已结束或不属于该执行者时返回 false）
     */
    @PostMapping("report/start")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Boolean> reportStart(@RequestBody TaskReportStartParam param) {
        return SfcJsonResult.getInstance(reportService.reportStart(param));
    }

    /**
     * 上报任务执行进度。
     *
     * @param param 上报参数
     * @return 空成功响应
     */
    @PostMapping("report/progress")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Object> reportProgress(@RequestBody TaskProgressReportParam param) {
        reportService.reportProgress(param);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 上报任务运行日志。
     *
     * @param param 上报参数
     * @return 空成功响应
     */
    @PostMapping("report/log")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Object> reportLog(@RequestBody TaskLogReportParam param) {
        reportService.reportLog(param);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 上报任务执行结束。
     *
     * @param param 上报参数
     * @return 上报是否被接受
     */
    @PostMapping("report/finish")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Boolean> reportFinish(@RequestBody TaskFinishReportParam param) {
        return SfcJsonResult.getInstance(reportService.reportFinish(param));
    }

    /**
     * 上报放弃任务执行权，任务会被重置回队列交由其执行者接管。
     *
     * @param param 上报参数
     * @return 任务是否已重置回等待中
     */
    @PostMapping("report/abandon")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Boolean> reportAbandon(@RequestBody TaskAbandonParam param) {
        return SfcJsonResult.getInstance(reportService.reportAbandon(param));
    }

    /**
     * 解析 worker 类型。
     *
     * @param kind 类型字符串
     * @return worker 类型，为空时默认为微服务
     */
    private TaskWorkerKind parseKind(String kind) {
        if (!SfcStringUtils.hasText(kind)) {
            return TaskWorkerKind.REMOTE_SERVICE;
        }
        try {
            return TaskWorkerKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            throw new SfcJsonException(400, "无效的worker类型: " + kind);
        }
    }
}
