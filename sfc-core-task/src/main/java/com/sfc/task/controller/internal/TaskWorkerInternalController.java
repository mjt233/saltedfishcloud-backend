package com.sfc.task.controller.internal;

import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskCancelParam;
import com.sfc.cloud.task.worker.protocol.TaskClaimParam;
import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import com.sfc.cloud.task.worker.protocol.TaskFinishReportParam;
import com.sfc.cloud.task.worker.protocol.TaskLogReportParam;
import com.sfc.cloud.task.worker.protocol.TaskProgressReportParam;
import com.sfc.cloud.task.worker.protocol.TaskReportStartParam;
import com.sfc.cloud.task.worker.protocol.TaskStateInfo;
import com.sfc.cloud.task.worker.protocol.TaskStateQueryParam;
import com.sfc.cloud.task.worker.protocol.TaskSubmitParam;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskCreateParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.report.TaskReportService;
import com.sfc.task.schedule.TaskClaimService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 异步任务执行者（worker）内部协议控制器。
 *
 * <p>{@value #PREFIX} 属于 {@code /api/internal/**} 前缀，由 {@code sfc-auth} SDK 的
 * {@code SfcInternalAuthFilter} 强制校验服务间调用凭证（请求头 {@code X-Sfc-Internal-Token} 中
 * 每次调用新签发的 HS256 JWT），客户端无法直接访问；网关侧也会直接拒绝该前缀。</p>
 *
 * <p>本控制器是任务执行者 SDK（core 内嵌执行者与微服务实例共用）与调度中心之间的完整协议面：
 * 执行者通过它认领任务、上报执行进度与结果，协议报文使用
 * {@code com.sfc.cloud.task.worker.protocol} 包中的统一定义；微服务也通过它按任务id批量查询
 * 状态与进度、取消任务（用于校准本地任务镜像与响应用户操作）。</p>
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

    /**
     * 单次批量查询任务状态允许的最大任务数量
     */
    private static final int MAX_QUERY_TASK_COUNT = 500;

    private final TaskClaimService claimService;

    private final TaskReportService reportService;

    private final AsyncTaskManager asyncTaskManager;

    private final AsyncTaskRecordRepo taskRecordRepo;

    private final UserService userService;

    /**
     * 认领一个任务，无任务时按请求的等待时长进行长轮询等待；可通过 {@code urgentOnly=true}
     * 只认领紧急任务。
     *
     * @param param 认领参数
     * @return 认领到的任务信息，无任务时为 null
     */
    @PostMapping("claim")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<TaskDispatchInfo> claim(@RequestBody TaskClaimParam param) {
        if (!SfcStringUtils.hasText(param.serviceId()) || !SfcStringUtils.hasText(param.workerId())) {
            throw new SfcJsonException(400, "serviceId 与 workerId 不能为空");
        }
        String role = SfcStringUtils.hasText(param.role())
                ? param.role()
                : AsyncTaskConstants.WorkerRole.SERVICE;
        int waitSeconds = param.waitSeconds() == null ? 0 : param.waitSeconds();
        AsyncTaskRecord record = claimService.claim(
                param.serviceId(),
                param.workerId(),
                role,
                param.types(),
                Boolean.TRUE.equals(param.urgentOnly()),
                waitSeconds);
        return SfcJsonResult.getInstance(record == null ? null : toDispatchInfo(record));
    }

    /**
     * 由任务记录构建下发给执行者的任务信息。
     *
     * @param record 任务记录
     * @return 下发给执行者的任务信息
     */
    private static TaskDispatchInfo toDispatchInfo(AsyncTaskRecord record) {
        return new TaskDispatchInfo(
                record.getId(),
                record.getName(),
                record.getTaskType(),
                record.getParams(),
                record.getUid(),
                record.getCpuOverhead(),
                record.getRetryCount(),
                record.getCreateAt());
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
        if (param.uid() != null && userService.getUserById(param.uid()) == null) {
            throw new SfcJsonException(400, "任务归属用户不存在: " + param.uid());
        }
        AsyncTaskCreateParam createParam = AsyncTaskCreateParam.builder()
                .name(param.name())
                .taskType(param.taskType())
                .params(param.params())
                .cpuOverhead(param.cpuOverhead())
                .isTemp(param.isTemp())
                .immediate(param.immediate())
                .build();
        AsyncTaskRecord record = asyncTaskManager.submitTaskForUser(param.uid(), createParam);
        return SfcJsonResult.getInstance(record.getId());
    }

    /**
     * 批量查询任务的状态与最新进度快照。
     *
     * <p>供微服务按任务id校准本地保存的任务状态镜像：返回结果只包含 core 中实际存在的任务，
     * 不存在的任务id会被忽略（调用方按 {@link TaskStateInfo#taskId()} 对齐）；进度来自 core 的
     * 进度快照缓存，任务没有快照时进度字段为 null。</p>
     *
     * @param param 查询参数
     * @return 任务状态与进度列表，可能为空
     */
    @PostMapping("query")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<List<TaskStateInfo>> query(@RequestBody TaskStateQueryParam param) {
        List<Long> taskIds = param == null ? null : param.taskIds();
        if (taskIds == null || taskIds.isEmpty()) {
            throw new SfcJsonException(400, "taskIds 不能为空");
        }
        if (taskIds.size() > MAX_QUERY_TASK_COUNT) {
            throw new SfcJsonException(400, "单次最多查询 " + MAX_QUERY_TASK_COUNT + " 个任务");
        }
        List<Long> ids = taskIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return SfcJsonResult.getInstance(List.of());
        }
        List<TaskStateInfo> result = new ArrayList<>(ids.size());
        for (AsyncTaskRecord record : taskRecordRepo.findAllById(ids)) {
            result.add(toTaskStateInfo(record));
        }
        return SfcJsonResult.getInstance(result);
    }

    /**
     * 由任务记录与最新进度快照构建任务状态信息。
     *
     * @param record 任务记录
     * @return 任务状态与进度信息
     */
    private TaskStateInfo toTaskStateInfo(AsyncTaskRecord record) {
        ProgressRecord progress = reportService.getProgress(record.getId());
        Long loaded = null;
        Long total = null;
        Long speed = null;
        if (progress != null) {
            loaded = progress.getLoaded();
            total = progress.getTotal();
            speed = progress.getSpeed();
        }
        Date finishDate = record.getFinishDate() != null ? record.getFinishDate() : record.getFailedDate();
        return new TaskStateInfo(record.getId(), record.getStatus(), loaded, total, speed, finishDate);
    }

    /**
     * 取消一个任务。
     *
     * <p>等待中的任务由 core 直接置为已取消（不会再被执行者认领）；运行中的任务由 core 按服务发现
     * 定位执行者实例地址后下发中断指令。中断是尽力而为的：执行者已失联或中断指令下发失败时返回 409，
     * 调用方可稍后重试或按业务语义处理。</p>
     *
     * @param param 取消参数
     * @return 空成功响应
     * @throws IOException 中断指令下发过程中出现 IO 异常时抛出
     */
    @PostMapping("cancel")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Object> cancel(@RequestBody TaskCancelParam param) throws IOException {
        Long taskId = param == null ? null : param.taskId();
        if (taskId == null) {
            throw new SfcJsonException(400, "taskId 不能为空");
        }
        try {
            asyncTaskManager.interrupt(taskId);
        } catch (IllegalArgumentException e) {
            throw new SfcJsonException(400, "取消失败：" + e.getMessage());
        } catch (IllegalStateException e) {
            throw new SfcJsonException(409, "取消失败：" + e.getMessage());
        }
        return SfcJsonResult.emptySuccess();
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
     * 上报放弃任务执行权，任务会被重置回队列交由其执行者接管。<br>
     * 临时任务不参与故障转移重试，放弃后直接置为失败。
     *
     * @param param 上报参数
     * @return 任务是否已重置回等待中（临时任务与超过重试上限的任务返回 false）
     */
    @PostMapping("report/abandon")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<Boolean> reportAbandon(@RequestBody TaskAbandonParam param) {
        return SfcJsonResult.getInstance(reportService.reportAbandon(param));
    }
}
