package com.sfc.download.task;

import com.sfc.cloud.exception.SfcRemoteApiException;
import com.sfc.cloud.model.SfcFileInfo;
import com.sfc.cloud.remote.client.SfcCoreFileClient;
import com.sfc.cloud.task.worker.SfcTaskDetachedException;
import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskHandler;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadEngine;
import com.sfc.download.engine.DownloadEngineRegistry;
import com.sfc.download.engine.DownloadTempDirCreator;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.engine.p2p.Aria2Workspace;
import com.sfc.download.engine.p2p.P2pTaskCoordinator;
import com.sfc.download.engine.p2p.P2pTaskCoordinator.P2pTaskResult;
import com.sfc.download.engine.p2p.P2pTaskState;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.repo.DownloadTaskRepo;
import com.sfc.download.stash.DownloadSaveService;
import com.sfc.download.util.DownloadFileUtils;
import com.sfc.download.util.DownloadLogUtils;
import com.sfc.download.util.DownloadTaskNameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 离线下载任务处理器（任务类型 {@code offline-download}）。
 *
 * <p>由 sfc-task-worker SDK 在认领到 {@link DownloadTaskConstants#TASK_TYPE} 类型的任务后调用
 * {@link #execute(SfcTaskExecution)}。本类只负责「调度与状态机」，<b>不包含任何具体的下载实现</b>：</p>
 * <ol>
 *     <li>解析任务参数 {@link DownloadTaskParams}，把本地任务状态镜像置为运行中；</li>
 *     <li>把 Worker 的中断/挂起指令接线到 {@link DownloadContext}，使引擎能够及时释放阻塞资源；</li>
 *     <li>通过 {@link SfcCoreFileClient#list(long, String)} 校验保存目录是否存在；</li>
 *     <li>非 P2P：按参数类型经 {@link DownloadEngineRegistry} 路由到具体下载引擎（HTTP / Git / ed2k）；
 *         P2P（磁力链 / 种子）：交由 {@link P2pTaskCoordinator} 编排（检查点、新建或接管、做种登记）；</li>
 *     <li>引擎执行完毕后，由 {@link DownloadSaveService} 统一把产物写入 core 网盘；</li>
 *     <li>回写任务展示名与完成状态；异常时按「取消 / 失败 / 挂起」三种语义回写并执行收口矩阵。</li>
 * </ol>
 *
 * <p><b>P2P 检查点与接管：</b>P2P 任务在下载、上传前后与挂起时由协调器保存检查点（{@link P2pTaskState} JSON）。
 * 执行者停机（挂起）时保存检查点后抛 {@link SfcTaskDetachedException}：任务回到队列，由其它实例接管；
 * 接管实例发现检查点为 {@code UPLOADED} 时直接补报 FINISH，不重复上传。</p>
 *
 * <p><b>生命周期收口矩阵：</b>P2P 任务的 aria2 任务结果与本地目录不再无条件删除，统一由
 * {@link P2pTaskCoordinator} 按「成功 + 做种保留 / 成功无做种清理 / 上传失败 / 取消 / 失败 / 挂起」决定。</p>
 *
 * <p><b>并发说明：</b>本处理器是 Spring 单例，会被 Worker 线程池并发调用，因此自身不持有任何
 * 任务级可变状态；一次执行的全部可变状态都保存在每次新建的 {@link DownloadContext} 中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadTaskHandler implements SfcTaskHandler {

    /**
     * 日志前缀：应用日志与任务运行日志共用，便于按前缀检索。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 写入 {@code download_task.message} 的错误信息最大长度，避免超长堆栈撑爆数据库行。
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    /**
     * 下载任务仓储：回写本地任务状态镜像（状态、大小、保存名、结束时间、失败信息）。
     */
    private final DownloadTaskRepo downloadTaskRepo;

    /**
     * core 网盘文件客户端：仅用于校验保存目录是否存在且当前用户可访问。
     */
    private final SfcCoreFileClient fileClient;

    /**
     * 下载引擎注册表：按下载类型路由到具体引擎。
     */
    private final DownloadEngineRegistry engineRegistry;

    /**
     * 下载产物落盘服务：把引擎产出的本地文件写入 core 网盘。
     */
    private final DownloadSaveService downloadSaveService;

    /**
     * aria2 共享工作区：为磁力链 / 种子任务提供跨容器共享目录中的任务目录创建能力。
     */
    private final Aria2Workspace aria2Workspace;

    /**
     * P2P 任务协调器：检查点、续传接管、做种登记与生命周期收口。
     */
    private final P2pTaskCoordinator p2pTaskCoordinator;

    /**
     * 本处理器支持的任务类型。
     *
     * @return {@link DownloadTaskConstants#TASK_TYPE}
     */
    @Override
    public String taskType() {
        return DownloadTaskConstants.TASK_TYPE;
    }

    /**
     * 声明本处理器支持「挂起」（执行者优雅停机时保留现场、任务回到队列等待接管）。
     *
     * <p>同一处理器同时承载 P2P 与非 P2P 任务：P2P 任务拥有 aria2 外部现场与检查点，挂起后可由任意实例接管；
     * HTTP / Git / ed2k 任务没有可接管的下载现场，收到挂起请求后按「取消语义收尾」（清理本地临时文件）并以
     * 挂起异常退出，由 SDK 无重试消耗地重新排队。</p>
     *
     * @return 恒定返回 {@code true}
     */
    @Override
    public boolean supportsDetach() {
        return true;
    }

    /**
     * 执行一个离线下载任务。
     *
     * <p><b>执行顺序：</b>参数解析 → 置运行中 → 中断接线 → 保存目录校验 → 类型路由（P2P 走协调器）→
     * 引擎/协调器执行 → 落盘 → 置完成 → 收口矩阵。</p>
     *
     * <p><b>异常映射约定：</b></p>
     * <ul>
     *     <li>挂起（{@link SfcTaskDetachedException}）→ 检查点已由协调器保存，<b>不动</b> aria2 与本地目录，
     *         异常原样抛出由 SDK 上报 abandon(detached)；</li>
     *     <li>中断（{@link SfcTaskInterruptedException}，或中断指令导致 IO 失败）→ 本地状态置 CANCEL，
     *         按收口矩阵清理后以中断语义抛出，由 SDK 上报 CANCEL；</li>
     *     <li>其它异常 → 本地状态置 FAILED 并记录错误信息，按收口矩阵清理后原样抛出，由 SDK 上报 FAILED。</li>
     * </ul>
     *
     * @param execution 任务执行上下文（任务参数、日志、进度与中断/挂起协作）
     * @throws SfcTaskInterruptedException 任务被中断时抛出（由 SDK 上报为 CANCEL）
     * @throws SfcTaskDetachedException    执行者停机导致任务挂起时抛出（由 SDK 重新排队，不计重试）
     * @throws RuntimeException            其它执行失败（由 SDK 上报为 FAILED）
     */
    @Override
    public void execute(SfcTaskExecution execution) {
        // 参数解析刻意放在 try 之前：此时还拿不到 downloadId，无法回写本地状态镜像，
        // 解析失败直接由 SDK 上报失败即可。
        DownloadTaskParams params = parseParams(execution);
        Long downloadId = params.downloadId;
        Long taskId = execution.getTaskId();
        long startNanos = System.nanoTime();
        // 临时目录策略按下载类型选择（必须在创建上下文之前确定）：
        // 磁力链 / 种子由 aria2 写盘，可能跨容器部署，任务目录需落在共享工作区，产物本服务才可见；
        // 其它类型在本服务进程内下载，沿用默认的 JVM 临时目录策略。
        DownloadType type = params.type;
        boolean isP2pTask = type == DownloadType.MAGNET || type == DownloadType.TORRENT;
        DownloadTempDirCreator tempDirCreator = isP2pTask ? aria2Workspace::createTaskDir : null;
        // 引擎解析出名称后（HTTP 响应文件名 / aria2 文件清单）立即回写，使任务在运行中即可展示名称
        DownloadContext context = new DownloadContext(execution, params, name -> updateTaskNameQuietly(downloadId, name), tempDirCreator);
        try {
            execution.log(LOG_PREFIX + "开始执行离线下载任务: " + params);
            log.info("{}开始执行离线下载任务: downloadId={}, taskId={}, uid={}, type={}, url={}, savePath={}",
                    LOG_PREFIX, downloadId, taskId, params.uid,
                    type == null ? "未指定" : type.getCode(),
                    DownloadLogUtils.maskUrl(params.url), params.savePath);
            downloadTaskRepo.updateStatus(downloadId, DownloadTaskConstants.Status.RUNNING);

            // 先接线再检查：既覆盖「接线前就已收到取消」的情况，也让引擎在自身注册取消回调时能立即感知
            execution.onInterrupt(context::cancel);
            context.checkInterrupted();

            // 类型只取创建任务时显式指定并落库的值：执行期不做任何推导或降级
            if (type == null) {
                throw new SfcJsonException(400, "离线下载任务参数缺少下载类型（type），拒绝执行");
            }
            context.log("使用下载方式: " + type.getDisplayName());
            ExecutionOutcome outcome = isP2pTask
                    ? executeP2p(context)
                    : executeStandard(context, type);
            log.info("{}离线下载任务执行完成: downloadId={}, taskId={}, type={}, 保存名={}, 任务名称={}, 大小={}字节, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, type.getCode(), outcome.savedName(), outcome.finishName(),
                    outcome.totalBytes(), elapsedMillis(startNanos));
        } catch (SfcTaskDetachedException e) {
            // 挂起：收口矩阵要求 aria2 与本地目录「不动」、检查点保留；由协调器在异常传播路径上保存
            execution.log(LOG_PREFIX + "任务已挂起，检查点已保存，等待其它实例接管: " + describe(e));
            log.info("{}离线下载任务已挂起: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, describe(e), elapsedMillis(startNanos));
            throw e;
        } catch (SfcTaskInterruptedException e) {
            execution.log(LOG_PREFIX + "任务已被取消: " + describe(e));
            log.info("{}离线下载任务已取消: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, describe(e), elapsedMillis(startNanos));
            updateCancelStatusQuietly(execution, downloadId);
            releaseP2pOnCancel(context);
            throw e;
        } catch (Exception e) {
            if (context.isCancelled() || execution.isInterruptRequested()) {
                // 中断回调会关闭下载流、终止外部下载任务，底层可能抛出任意异常类型，这里统一还原为「取消」语义
                String reason = describe(e);
                execution.log(LOG_PREFIX + "任务因中断而终止: " + reason);
                log.info("{}离线下载任务因中断而终止: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                        LOG_PREFIX, downloadId, taskId, reason, elapsedMillis(startNanos));
                updateCancelStatusQuietly(execution, downloadId);
                releaseP2pOnCancel(context);
                throw new SfcTaskInterruptedException("离线下载任务被中断: " + reason, e);
            }
            String message = describe(e);
            execution.log(LOG_PREFIX + "任务执行失败: " + message);
            // 异常堆栈由 Worker SDK 统一以 ERROR 级别输出，这里只补一条带任务上下文的应用日志
            log.warn("{}离线下载任务执行失败: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, message, elapsedMillis(startNanos));
            updateFailureQuietly(execution, downloadId, message);
            releaseP2pOnFailure(context);
            throw e;
        } finally {
            // P2P 任务目录由收口矩阵决定保留或删除；非 P2P 任务仍按原行为无条件清理临时目录
            cleanup(context, isP2pTask);
        }
    }

    // ==================== 执行分支 ====================

    /**
     * 执行非 P2P 下载（HTTP / Git / ed2k）：引擎下载 → 落盘 → 回写完成。
     *
     * @param context 执行上下文
     * @param type    下载类型
     * @return 执行结果摘要（保存名、上传字节数、任务展示名）
     */
    private ExecutionOutcome executeStandard(DownloadContext context, DownloadType type) {
        Long downloadId = context.getDownloadId();
        log.info("{}下载任务已路由到引擎: downloadId={}, taskId={}, type={}, engineId={}",
                LOG_PREFIX, downloadId, context.getExecution().getTaskId(), type.getCode(),
                engineRegistry.get(type).getEngineId());

        validateSavePath(context);
        DownloadEngine engine = engineRegistry.get(type);
        List<DownloadArtifact> artifacts = engine.execute(context);
        log.info("{}下载引擎执行完成: downloadId={}, taskId={}, type={}, 产物数={}",
                LOG_PREFIX, downloadId, context.getExecution().getTaskId(), type.getCode(),
                artifacts == null ? 0 : artifacts.size());

        // 非 P2P 任务没有可接管的下载现场：收到挂起请求时清理本地临时目录（finally）后退出等待重新排队
        context.checkDetach();
        // 落盘前再检查一次，避免把已被取消的内容写入网盘
        context.checkInterrupted();
        // 完成前先把展示名落库：上传可能耗时较长，期间列表也能看到名称；上传失败时名称同样保留
        String taskDisplayName = resolveTaskDisplayName(type, artifacts);
        if (SfcStringUtils.hasText(taskDisplayName)) {
            updateTaskNameQuietly(downloadId, taskDisplayName);
            context.log("任务名称: " + taskDisplayName);
        }
        DownloadSaveService.StashResult result = stash(context, artifacts);

        // P2P 下载使用文件名摘要作为任务名称；其它类型沿用落盘结果的保存名
        String finishName = SfcStringUtils.hasText(taskDisplayName) ? taskDisplayName : result.savedName();
        downloadTaskRepo.updateFinish(downloadId, DownloadTaskConstants.Status.FINISH,
                result.totalBytes(), result.totalBytes(), finishName, new Date());
        return new ExecutionOutcome(result.savedName(), result.totalBytes(), finishName);
    }

    /**
     * 执行 P2P 下载（磁力链 / 种子）：协调器新建或接管 → 上传幂等 → 收口矩阵。
     *
     * <p>接管分支按检查点阶段处理：</p>
     * <ul>
     *   <li>{@code UPLOADED}：直接补报 FINISH，不重复上传；</li>
     *   <li>{@code UPLOADING}：先做上传幂等等待（新检查点可能已变为 UPLOADED），否则采集既有产物重新上传；</li>
     *   <li>其它阶段：交给协调器恢复（继续轮询 / 采集产物 / gid 丢失同目录续传）。</li>
     * </ul>
     *
     * @param context 执行上下文
     * @return 执行结果摘要（保存名、上传字节数、任务展示名）
     */
    private ExecutionOutcome executeP2p(DownloadContext context) {
        DownloadTaskParams params = context.getParams();
        Long downloadId = context.getDownloadId();
        P2pTaskState state = p2pTaskCoordinator.loadResumable(context);
        if (state != null) {
            log.info("{}检测到可接管的 P2P 检查点: downloadId={}, phase={}, gid={}",
                    LOG_PREFIX, downloadId, state.getPhase(), state.getGids());
        }
        // 分支 1：上次已上传成功但未上报完成 → 直接补报 FINISH
        if (state != null && state.isUploaded()) {
            context.log("检测到上次上传已完成（检查点 phase=UPLOADED），直接补报任务完成，不重复上传");
            return finalizeP2pFromCheckpoint(context, state);
        }

        List<DownloadArtifact> artifacts;
        if (state != null && state.isPhase(P2pTaskState.Phase.UPLOADING)) {
            // 分支 2：上传阶段幂等
            P2pTaskState latest = p2pTaskCoordinator.awaitUploadSettled(context, state).orElse(state);
            if (latest.isUploaded()) {
                context.log("等待期间确认上传已完成，直接补报任务完成，不重复上传");
                return finalizeP2pFromCheckpoint(context, latest);
            }
            validateSavePath(context);
            P2pTaskResult collected = p2pTaskCoordinator.collectExisting(latest, context);
            state = collected.state();
            artifacts = collected.artifacts();
        } else if (state != null) {
            // 分支 3：接管下载阶段
            validateSavePath(context);
            P2pTaskResult resumed = p2pTaskCoordinator.resume(state, context);
            state = resumed.state();
            artifacts = resumed.artifacts();
        } else {
            // 分支 4：新建任务
            validateSavePath(context);
            P2pTaskResult started = p2pTaskCoordinator.start(context);
            state = started.state();
            artifacts = started.artifacts();
        }

        // 下载完成后、开始上传前：响应挂起与取消（挂起时 aria2/目录/检查点保持不动）
        context.checkDetach();
        context.checkInterrupted();
        // 完成前先把展示名落库：上传可能耗时较长，期间列表也能看到名称；上传失败时名称同样保留
        String taskDisplayName = resolveTaskDisplayName(params.type, artifacts);
        if (SfcStringUtils.hasText(taskDisplayName)) {
            updateTaskNameQuietly(downloadId, taskDisplayName);
            context.log("任务名称: " + taskDisplayName);
        }

        // 上传前写检查点（UPLOADING + uploadStartedAt），成功后写 UPLOADED + savedName/savedBytes
        p2pTaskCoordinator.markUploading(context, state);
        DownloadSaveService.StashResult result = stash(context, artifacts);
        p2pTaskCoordinator.markUploaded(context, state, result.savedName(), result.totalBytes());

        // 上传阶段收到挂起：允许上传完成，写 UPLOADED 后退出，由接管实例直接补报 FINISH
        if (context.isDetachRequested()) {
            context.log("上传已完成并保存检查点（phase=UPLOADED），任务挂起，等待接管实例补报完成");
            throw new SfcTaskDetachedException("P2P 任务上传完成，执行者停机，任务已挂起");
        }
        return finalizeP2pSuccess(context, state, taskDisplayName, result);
    }

    /**
     * 补报 P2P 任务完成（检查点已是 {@code UPLOADED}）。
     *
     * @param context 执行上下文
     * @param state   检查点
     * @return 执行结果摘要
     */
    private ExecutionOutcome finalizeP2pFromCheckpoint(DownloadContext context, P2pTaskState state) {
        context.checkDetach();
        String savedName = state.getSavedName();
        long savedBytes = state.getSavedBytes();
        downloadTaskRepo.updateFinish(context.getDownloadId(), DownloadTaskConstants.Status.FINISH,
                savedBytes, savedBytes, savedName, new Date());
        p2pTaskCoordinator.finishSuccess(context, state);
        context.log("已补报任务完成: " + (savedName == null ? "（保存名未知）" : savedName)
                + "（" + savedBytes + " 字节）");
        return new ExecutionOutcome(savedName, savedBytes, savedName);
    }

    /**
     * 正常完成 P2P 任务并执行成功收口（做种保留或清理）。
     *
     * @param context         执行上下文
     * @param state           检查点
     * @param taskDisplayName 任务展示名，允许为 {@code null}
     * @param result          落盘结果
     * @return 执行结果摘要
     */
    private ExecutionOutcome finalizeP2pSuccess(DownloadContext context, P2pTaskState state,
                                                String taskDisplayName, DownloadSaveService.StashResult result) {
        String finishName = SfcStringUtils.hasText(taskDisplayName) ? taskDisplayName : result.savedName();
        downloadTaskRepo.updateFinish(context.getDownloadId(), DownloadTaskConstants.Status.FINISH,
                result.totalBytes(), result.totalBytes(), finishName, new Date());
        p2pTaskCoordinator.finishSuccess(context, state);
        return new ExecutionOutcome(result.savedName(), result.totalBytes(), finishName);
    }

    // ==================== 执行步骤 ====================

    /**
     * 解析任务参数。
     *
     * <p>只做「必须能拿到任务主键」的最小校验：参数为空、JSON 非法、缺少 {@code downloadId}
     * 时直接抛 {@link SfcJsonException}（此时无法回写本地状态镜像）；其余参数校验在创建任务阶段
     * 已由 {@link DownloadEngineRegistry#validate(DownloadTaskParams)} 完成。</p>
     *
     * @param execution 任务执行上下文
     * @return 解析后的任务参数
     * @throws SfcJsonException 参数为空、格式非法或缺少 {@code downloadId} 时抛出
     */
    private DownloadTaskParams parseParams(SfcTaskExecution execution) {
        String rawParams = execution.getParams();
        if (!SfcStringUtils.hasText(rawParams)) {
            throw new SfcJsonException(400, "离线下载任务参数为空");
        }
        DownloadTaskParams params;
        try {
            params = SfcMapperHolder.parseJson(rawParams, DownloadTaskParams.class);
        } catch (IOException e) {
            throw new SfcJsonException(400, "离线下载任务参数解析失败: " + e.getMessage());
        }
        if (params == null) {
            throw new SfcJsonException(400, "离线下载任务参数为空");
        }
        if (params.downloadId == null) {
            throw new SfcJsonException(400, "离线下载任务参数缺少 downloadId");
        }
        return params;
    }

    /**
     * 通过 core 的内部文件接口校验保存目录是否存在且可访问。
     *
     * <p>任何 core 侧的失败（路径不存在、非法路径、无权限）都归一为「无效的保存路径」业务异常；
     * 这里只做存在性校验，真正的落盘由 {@link DownloadSaveService} 在下载完成后执行。</p>
     *
     * @param context 任务执行上下文
     * @throws SfcJsonException 保存目录不存在或无权访问时抛出（HTTP 400 语义）
     */
    private void validateSavePath(DownloadContext context) {
        DownloadTaskParams params = context.getParams();
        context.log("校验保存目录: uid=" + params.uid + ", path=" + params.savePath);
        try {
            SfcJsonResult<List<SfcFileInfo>> result = fileClient.list(params.uid, params.savePath);
            if (result == null) {
                throw new SfcJsonException(400, "无效的保存路径:" + params.savePath);
            }
            result.requireSuccess();
        } catch (SfcRemoteApiException e) {
            log.warn("{}校验保存目录失败: downloadId={}, uid={}, path={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), params.uid, params.savePath, e.getMessage());
            throw new SfcJsonException(400, "无效的保存路径:" + params.savePath);
        }
        context.log("保存目录校验通过");
        log.info("{}保存目录校验通过: downloadId={}, uid={}, path={}",
                LOG_PREFIX, context.getDownloadId(), params.uid, params.savePath);
    }

    /**
     * 把引擎产出的产物写入 core 网盘。
     *
     * <p>顶层目录名刻意传 {@code null}：由落盘服务按「全部产物的公共首层目录」推断
     * （Git 仓库为仓库名、多文件种子为种子信息里的名称），单文件场景则直接走单文件通道。</p>
     *
     * @param context   任务执行上下文
     * @param artifacts 下载引擎产出的产物列表，允许为 {@code null}
     * @return 落盘结果（最终保存名与实际上传字节数）
     * @throws SfcJsonException 产物为空、目录创建失败或上传失败时抛出
     */
    private DownloadSaveService.StashResult stash(DownloadContext context, List<DownloadArtifact> artifacts) {
        DownloadTaskParams params = context.getParams();
        long uploadStartNanos = System.nanoTime();
        context.log("开始保存文件到网盘: uid=" + params.uid + ", path=" + params.savePath);
        log.info("{}开始保存文件到网盘: downloadId={}, uid={}, path={}, 产物数={}",
                LOG_PREFIX, context.getDownloadId(), params.uid, params.savePath,
                artifacts == null ? 0 : artifacts.size());
        DownloadSaveService.StashResult result = downloadSaveService.stash(
                new DownloadSaveService.StashRequest(params.uid, params.savePath, null, artifacts));
        context.log("文件保存成功: " + result.savedName() + "（" + result.totalBytes() + " 字节）");
        log.info("{}文件保存成功: downloadId={}, uid={}, path={}, 保存名={}, 大小={}字节, 上传耗时={}ms",
                LOG_PREFIX, context.getDownloadId(), params.uid, params.savePath,
                result.savedName(), result.totalBytes(), elapsedMillis(uploadStartNanos));
        return result;
    }

    /**
     * 解析任务完成后的展示名称。
     *
     * <p>仅 P2P 下载（{@link DownloadType#MAGNET} / {@link DownloadType#TORRENT}）使用「下载文件名摘要」：
     * 单个文件直接使用文件名；两到三个文件完整拼接；超过三个文件只列前三个并在末尾追加「等...」。
     * 规则实现见 {@link DownloadTaskNameUtils}。</p>
     *
     * <p>其它类型（HTTP / GIT / ed2k）返回 {@code null}，由调用方继续使用落盘结果中的保存名；
     * 没有有效文件名时同样返回 {@code null}，避免用空名称覆盖已有名称。</p>
     *
     * @param type      下载类型，允许为 {@code null}
     * @param artifacts 下载产物列表，允许为 {@code null}
     * @return 任务展示名称；非 P2P 类型或没有有效文件名时返回 {@code null}
     */
    private static String resolveTaskDisplayName(DownloadType type, List<DownloadArtifact> artifacts) {
        if (type != DownloadType.MAGNET && type != DownloadType.TORRENT) {
            return null;
        }
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }
        List<String> fileNames = new ArrayList<>(artifacts.size());
        for (DownloadArtifact artifact : artifacts) {
            if (artifact != null) {
                fileNames.add(artifact.relativePath());
            }
        }
        return DownloadTaskNameUtils.buildDisplayName(fileNames);
    }

    /**
     * 静默回写任务展示名称。
     *
     * <p>名称只是列表展示用的镜像字段：回写失败只记 WARN，不影响下载、落盘与任务状态；
     * 成功完成时 {@code updateFinish} 还会再写入一次最终名称。</p>
     *
     * @param downloadId 下载任务 id
     * @param name       任务展示名称，允许为 {@code null} 或空白
     */
    private void updateTaskNameQuietly(Long downloadId, String name) {
        if (!SfcStringUtils.hasText(name)) {
            return;
        }
        try {
            downloadTaskRepo.updateName(downloadId, name);
        } catch (RuntimeException e) {
            log.warn("{}回写下载任务名称失败: downloadId={}, name={}, cause={}",
                    LOG_PREFIX, downloadId, name, e.toString());
        }
    }

    /**
     * 任务收尾清理。
     *
     * <p>非 P2P：删除本次任务创建的本地临时目录（递归），best-effort。P2P：<b>不做任何清理</b>——
     * aria2 任务结果与本地目录一律由收口矩阵决定（成功保留做种 / 成功清理 / 失败清理 / 取消清理 /
     * 挂起保留），避免无条件删除破坏续传或做种现场。</p>
     *
     * @param context  任务执行上下文
     * @param isP2pTask 是否为 P2P 任务
     */
    private void cleanup(DownloadContext context, boolean isP2pTask) {
        if (isP2pTask) {
            log.debug("{}P2P 任务目录由生命周期收口矩阵处理，跳过无条件清理: downloadId={}",
                    LOG_PREFIX, context.getDownloadId());
            return;
        }
        try {
            Path tempDir = context.getTempDir();
            log.info("{}清理下载临时目录: downloadId={}, path={}",
                    LOG_PREFIX, context.getDownloadId(), tempDir);
            DownloadFileUtils.deleteQuietly(tempDir);
        } catch (RuntimeException e) {
            log.warn("{}清理下载临时目录失败: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), e.toString());
        }
    }

    /**
     * 失败时按收口矩阵处理 P2P 现场（异常只记日志，不覆盖原始失败）。
     *
     * @param context 任务执行上下文
     */
    private void releaseP2pOnFailure(DownloadContext context) {
        P2pTaskState state = context.getP2pState();
        if (state == null) {
            return;
        }
        try {
            p2pTaskCoordinator.releaseOnFailure(context, state);
        } catch (RuntimeException e) {
            log.warn("{}失败收口清理异常（忽略）: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), e.toString());
        }
    }

    /**
     * 取消时按收口矩阵处理 P2P 现场（异常只记日志，不覆盖原始取消）。
     *
     * @param context 任务执行上下文
     */
    private void releaseP2pOnCancel(DownloadContext context) {
        P2pTaskState state = context.getP2pState();
        if (state == null) {
            return;
        }
        try {
            p2pTaskCoordinator.releaseOnCancel(context, state);
        } catch (RuntimeException e) {
            log.warn("{}取消收口清理异常（忽略）: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), e.toString());
        }
    }

    // ==================== 状态回写与日志工具 ====================

    /**
     * 静默把本地任务状态回写为「已取消」：回写失败只写运行日志，不覆盖原始异常。
     *
     * @param execution  任务执行上下文
     * @param downloadId 下载任务 id
     */
    private void updateCancelStatusQuietly(SfcTaskExecution execution, Long downloadId) {
        try {
            downloadTaskRepo.updateStatus(downloadId, DownloadTaskConstants.Status.CANCEL);
        } catch (RuntimeException e) {
            execution.log(LOG_PREFIX + "回写本地任务状态失败: " + describe(e));
        }
    }

    /**
     * 静默回写本地任务失败信息：回写失败只写运行日志，不覆盖原始异常。
     *
     * @param execution  任务执行上下文
     * @param downloadId 下载任务 id
     * @param message    错误信息
     */
    private void updateFailureQuietly(SfcTaskExecution execution, Long downloadId, String message) {
        try {
            downloadTaskRepo.updateFailure(downloadId, DownloadTaskConstants.Status.FAILED,
                    truncateMessage(message), new Date());
        } catch (RuntimeException e) {
            execution.log(LOG_PREFIX + "回写本地任务失败信息失败: " + describe(e));
        }
    }

    /**
     * 把异常转换为可读的简短描述。
     *
     * @param e 异常，允许为 {@code null}
     * @return 异常描述，永不为 {@code null}
     */
    private static String describe(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        String message = e.getMessage();
        if (!SfcStringUtils.hasText(message) && e.getCause() != null) {
            message = e.getCause().getMessage();
        }
        return SfcStringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }

    /**
     * 截断错误信息到数据库可接受的长度，并避免把代理对（surrogate pair）从中间切开。
     *
     * @param message 原始错误信息，允许为 {@code null}
     * @return 截断后的错误信息
     */
    private static String truncateMessage(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        int end = MAX_ERROR_MESSAGE_LENGTH;
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end) + "...";
    }

    /**
     * 计算从指定纳秒时间戳到当前时刻的耗时。
     *
     * @param startNanos 起始时间（取自 {@link System#nanoTime()}）
     * @return 耗时毫秒数
     */
    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /**
     * 一次执行的结果摘要（仅用于收尾日志）。
     *
     * @param savedName   网盘最终保存名
     * @param totalBytes  上传成功的字节数合计
     * @param finishName  写入 {@code download_task.name} 的展示名
     */
    private record ExecutionOutcome(String savedName, long totalBytes, String finishName) {
    }
}
