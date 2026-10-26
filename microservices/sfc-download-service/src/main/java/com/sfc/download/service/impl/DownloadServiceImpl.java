package com.sfc.download.service.impl;

import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.cloud.exception.SfcRemoteApiException;
import com.sfc.cloud.model.SfcFileInfo;
import com.sfc.cloud.model.SfcProxyInfo;
import com.sfc.cloud.remote.client.SfcCoreFileClient;
import com.sfc.cloud.remote.client.SfcCoreProxyClient;
import com.sfc.cloud.task.worker.SfcTaskWorkerClient;
import com.sfc.cloud.task.worker.protocol.TaskStateInfo;
import com.sfc.cloud.task.worker.protocol.TaskSubmitParam;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.identity.SfcCurrentUserSupport;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.model.param.TaskType;
import com.sfc.download.model.po.DownloadTaskInfo;
import com.sfc.download.model.vo.AsyncTaskRef;
import com.sfc.download.repo.DownloadTaskRepo;
import com.sfc.download.service.DownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 离线下载服务实现。
 *
 * <p><b>与 core 的协作边界：</b></p>
 * <ul>
 *   <li>保存路径校验、代理查询、文件上传等网盘能力经 {@code sfc-remote-client} 回调 core；</li>
 *   <li>任务提交、状态/进度查询、取消经 {@code sfc-task-worker} SDK 走 core 的内部任务接口；</li>
 *   <li>本服务只保存任务表中的状态<b>镜像</b>；列表查询时用 core 的实时状态校准，
 *       core 不可用时回退镜像（fail-soft，只记 WARN）。</li>
 * </ul>
 *
 * @see DownloadService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadServiceImpl implements DownloadService {

    /** 日志前缀 */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /** 离线下载任务的 CPU 开销指数（纯 IO 密集，与旧插件提交值一致） */
    private static final int CPU_OVERHEAD = 20;

    /** 单次向 core 查询任务状态的最大数量（core 侧硬限制 500，超出会返回 400） */
    private static final int MAX_STATE_QUERY_BATCH = 500;

    /** 下载中（非终态）状态集合：等待中 + 运行中 */
    private static final Collection<Integer> DOWNLOADING_STATUSES = List.of(
            DownloadTaskConstants.Status.WAITING,
            DownloadTaskConstants.Status.RUNNING
    );

    /** 已完成状态集合 */
    private static final Collection<Integer> FINISH_STATUSES = List.of(
            DownloadTaskConstants.Status.FINISH
    );

    /** 失败状态集合：失败 + 取消 + 离线 */
    private static final Collection<Integer> FAILED_STATUSES = List.of(
            DownloadTaskConstants.Status.FAILED,
            DownloadTaskConstants.Status.CANCEL,
            DownloadTaskConstants.Status.OFFLINE
    );

    /** 下载任务仓储 */
    private final DownloadTaskRepo downloadTaskRepo;

    /** core 网盘文件客户端（校验保存路径） */
    private final SfcCoreFileClient fileClient;

    /** core 代理查询客户端 */
    private final SfcCoreProxyClient proxyClient;

    /** core 异步任务客户端（提交 / 批量查状态 / 取消） */
    private final SfcTaskWorkerClient taskWorkerClient;

    /**
     * {@inheritDoc}
     *
     * <p>执行顺序：校验保存路径 → 解析代理（按名称）→ 落库等待中记录 → 提交 core 异步任务 →
     * 回写异步任务 id。提交失败时把任务标记为失败并抛出原异常，避免出现"本地有任务、core 无记录"
     * 却仍显示为等待中的假象。</p>
     */
    @Override
    public String createTask(DownloadTaskParams params, long creator) {
        log.info("{}收到创建离线下载任务请求: uid={}, creator={}, url={}, savePath={}, method={}, proxy={}",
                LOG_PREFIX, params.uid, creator, params.url, params.savePath, params.method,
                SfcStringUtils.hasText(params.proxy) ? params.proxy : "直连");
        validateSavePath(params.uid, params.savePath);
        resolveProxy(params, creator);

        DownloadTaskInfo task = new DownloadTaskInfo();
        task.setUrl(params.url);
        task.setProxy(params.proxy);
        task.setUid(params.uid);
        task.setCreatedBy(creator);
        task.setSavePath(params.savePath);
        task.setStatus(DownloadTaskConstants.Status.WAITING);
        task = downloadTaskRepo.save(task);
        log.info("{}下载任务已创建: downloadId={}, uid={}, creator={}, savePath={}",
                LOG_PREFIX, task.getId(), params.uid, creator, params.savePath);

        // 任务处理器从参数 JSON 中回读 downloadId，用于执行期回写进度/状态
        params.downloadId = task.getId();
        try {
            Long asyncTaskId = taskWorkerClient.submit(new TaskSubmitParam(
                    DownloadTaskConstants.TASK_NAME,
                    DownloadTaskConstants.TASK_TYPE,
                    SfcMapperHolder.toJsonNoEx(params),
                    params.uid,
                    CPU_OVERHEAD,
                    false,
                    false
            ));
            task.setTaskId(asyncTaskId);
            downloadTaskRepo.save(task);
            log.info("{}已提交异步任务: downloadId={}, taskId={}", LOG_PREFIX, task.getId(), asyncTaskId);
        } catch (RuntimeException e) {
            log.error("{}提交离线下载任务失败: downloadId={}, url={}", LOG_PREFIX, task.getId(), params.url, e);
            downloadTaskRepo.updateFailure(task.getId(), DownloadTaskConstants.Status.FAILED,
                    e.getMessage(), new Date());
            throw e;
        }
        return String.valueOf(task.getId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>权限口径：仅任务创建者或管理员可中断（{@link SfcUIDValidator#validateWithException(long, boolean)}）；
     * 状态口径：只有等待中 / 运行中的任务可中断。</p>
     */
    @Override
    public void interrupt(Long taskId) {
        if (taskId == null) {
            throw new SfcJsonException(400, "下载任务id不能为空");
        }
        log.info("{}收到下载任务中断请求: downloadId={}", LOG_PREFIX, taskId);
        DownloadTaskInfo task = downloadTaskRepo.findById(taskId)
                .orElseThrow(() -> new SfcJsonException(404, "下载任务不存在: " + taskId));
        SfcUIDValidator.validateWithException(task.getCreatedBy(), false);

        Integer status = task.getStatus();
        if (status == null || !DOWNLOADING_STATUSES.contains(status)) {
            throw new SfcJsonException(400, "只能对下载中或等待中的下载任务操作");
        }
        Long asyncTaskId = task.getTaskId();
        if (asyncTaskId == null) {
            throw new SfcJsonException(400, "任务尚未提交到异步任务系统，无法中断");
        }
        taskWorkerClient.cancel(asyncTaskId);
        log.info("{}下载任务中断指令已下发: downloadId={}, taskId={}, status={}",
                LOG_PREFIX, task.getId(), asyncTaskId, status);

        // 等待中的任务不会被执行者感知，core 只会把异步任务置为已取消，这里同步把镜像置为取消；
        // 运行中的任务由执行者在中断后自行回写状态镜像（避免与执行线程竞争写同一行）
        if (Objects.equals(DownloadTaskConstants.Status.WAITING, status)) {
            downloadTaskRepo.updateStatus(task.getId(), DownloadTaskConstants.Status.CANCEL);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>状态校准逻辑：</b>先按本地状态镜像分页查询，再对「非终态且已提交异步任务」的行批量调用
     * core 的 {@code queryTasks}，用 core 的实时状态/进度覆盖响应数据，并在状态变化时回写本地镜像。
     * core 调用失败只记 WARN 并回退镜像（fail-soft），不影响列表可用性。</p>
     */
    @Override
    public Page<DownloadTaskInfo> getTaskList(long uid, int page, int size, TaskType type) {
        Page<DownloadTaskInfo> tasks = queryPage(uid, type, PageRequest.of(page, size));
        List<DownloadTaskInfo> content = tasks.getContent();
        if (content.isEmpty()) {
            return tasks;
        }

        // 为每行生成前端契约中的 asyncTaskRecord 快照（先用本地镜像状态，后续校准会覆盖非终态行）
        content.forEach(task -> task.setAsyncTaskRecord(new AsyncTaskRef(task.getTaskId(), task.getStatus())));

        List<DownloadTaskInfo> activeTasks = content.stream()
                .filter(task -> task.getTaskId() != null && !isFinalStatus(task.getStatus()))
                .toList();
        if (!activeTasks.isEmpty()) {
            calibrateActiveTasks(activeTasks);
        }
        return tasks;
    }

    /**
     * {@inheritDoc}
     *
     * <p>代理归属 core，本服务只透传：未登录返回空列表；调用 core 失败时转换为业务异常。</p>
     */
    @Override
    public List<SfcProxyInfo> listAvailableProxy() {
        Long uid = SfcCurrentUserSupport.getCurrentUid();
        if (uid == null) {
            return List.of();
        }
        try {
            SfcJsonResult<List<SfcProxyInfo>> result = proxyClient.listAvailable(uid);
            if (result == null) {
                return List.of();
            }
            result.requireSuccess();
            return result.getData() == null ? List.of() : result.getData();
        } catch (SfcRemoteApiException e) {
            log.warn("{}获取可用代理列表失败: uid={}, cause={}", LOG_PREFIX, uid, e.getMessage());
            throw new SfcJsonException(500, "获取可用代理列表失败：" + e.getMsg());
        }
    }

    // ── 内部实现 ──

    /**
     * 校验保存路径是否存在且当前用户可访问。
     *
     * <p>复用 core 的目录列表接口做校验：拿到目录内容即说明路径存在且可读。
     * 任何 core 侧的失败（路径不存在、非法路径、无权限）都归一为「无效的保存路径」业务异常。</p>
     *
     * @param uid      资源归属用户 uid
     * @param savePath 保存目录路径
     * @throws SfcJsonException 路径无效时抛出（HTTP 400 语义）
     */
    private void validateSavePath(long uid, String savePath) {
        try {
            SfcJsonResult<List<SfcFileInfo>> result = fileClient.list(uid, savePath);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new SfcJsonException(400, "无效的保存路径:" + savePath);
            }
        } catch (SfcRemoteApiException e) {
            log.warn("{}校验保存路径失败: uid={}, savePath={}, cause={}", LOG_PREFIX, uid, savePath, e.getMessage());
            throw new SfcJsonException(400, "无效的保存路径:" + savePath);
        }
    }

    /**
     * 按代理名称解析代理节点，并把连接信息快照进任务参数。
     *
     * <p>旧插件保存的是代理 id，前端传的是代理名称；新契约统一按名称解析，并在创建任务时把
     * 类型 / 地址 / 端口快照进参数，避免任务执行时代理配置已被删除。</p>
     *
     * @param params  下载参数（{@code proxy} 非空时会被解析并回填快照字段）
     * @param creator 任务创建者 uid（用于计算该用户可见的代理范围）
     * @throws SfcJsonException 代理不存在或 core 调用失败时抛出
     */
    private void resolveProxy(DownloadTaskParams params, long creator) {
        String proxyName = params.proxy;
        if (proxyName == null || proxyName.isBlank()) {
            return;
        }
        List<SfcProxyInfo> proxies;
        try {
            SfcJsonResult<List<SfcProxyInfo>> result = proxyClient.listAvailable(creator).requireSuccess();
            proxies = result.getData() == null ? List.of() : result.getData();
        } catch (SfcRemoteApiException e) {
            log.warn("{}获取可用代理列表失败: creator={}, cause={}", LOG_PREFIX, creator, e.getMessage());
            throw new SfcJsonException(500, "获取可用代理列表失败：" + e.getMsg());
        }
        String targetName = proxyName.trim();
        SfcProxyInfo proxy = proxies.stream()
                .filter(item -> item != null && targetName.equals(item.getName()))
                .findFirst()
                .orElseThrow(() -> new SfcJsonException(400, "无效的代理：" + proxyName));
        params.proxy = proxy.getName();
        params.proxyType = proxy.getType();
        params.proxyAddress = proxy.getAddress();
        params.proxyPort = proxy.getPort();
    }

    /**
     * 按筛选类型分页查询下载任务。
     *
     * @param uid      任务归属用户 uid
     * @param type     筛选类型；{@code null} 或 {@link TaskType#ALL} 表示不过滤
     * @param pageable 分页参数
     * @return 分页查询结果（按 id 倒序）
     */
    private Page<DownloadTaskInfo> queryPage(long uid, TaskType type, Pageable pageable) {
        if (type == null || type == TaskType.ALL) {
            return downloadTaskRepo.findByUid(uid, pageable);
        }
        Collection<Integer> statuses;
        if (type == TaskType.DOWNLOADING) {
            statuses = DOWNLOADING_STATUSES;
        } else if (type == TaskType.FINISH) {
            statuses = FINISH_STATUSES;
        } else {
            statuses = FAILED_STATUSES;
        }
        return downloadTaskRepo.findByUidAndStatusIn(uid, statuses, pageable);
    }

    /**
     * 用 core 的实时状态与进度校准非终态下载任务。
     *
     * <p>core 调用失败时只记录 WARN 并直接返回（fail-soft）：列表继续使用本地镜像状态，
     * 保证 core 短暂不可用不会导致前端列表接口整体失败。</p>
     *
     * @param activeTasks 需要校准的任务（本地状态非终态且已关联异步任务）
     */
    private void calibrateActiveTasks(List<DownloadTaskInfo> activeTasks) {
        Map<Long, TaskStateInfo> stateMap = new HashMap<>();
        try {
            List<Long> taskIds = activeTasks.stream().map(DownloadTaskInfo::getTaskId).toList();
            for (int from = 0; from < taskIds.size(); from += MAX_STATE_QUERY_BATCH) {
                int to = Math.min(from + MAX_STATE_QUERY_BATCH, taskIds.size());
                for (TaskStateInfo state : taskWorkerClient.queryTasks(taskIds.subList(from, to))) {
                    if (state != null && state.taskId() != null) {
                        stateMap.put(state.taskId(), state);
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("{}批量查询异步任务状态失败，回退本地状态镜像: taskIds={}, cause={}",
                    LOG_PREFIX, activeTasks.stream().map(DownloadTaskInfo::getTaskId).toList(), e.toString());
            return;
        }
        for (DownloadTaskInfo task : activeTasks) {
            TaskStateInfo state = stateMap.get(task.getTaskId());
            if (state != null) {
                applyState(task, state);
            }
        }
    }

    /**
     * 把 core 返回的任务状态/进度快照应用到下载任务上，并在状态变化时回写本地镜像。
     *
     * @param task  下载任务
     * @param state core 返回的状态与进度快照
     */
    private void applyState(DownloadTaskInfo task, TaskStateInfo state) {
        if (state.loaded() != null) {
            task.setLoaded(state.loaded());
        }
        if (state.total() != null) {
            task.setSize(state.total());
        }
        if (state.speed() != null) {
            task.setSpeed(state.speed());
        }
        if (state.finishDate() != null) {
            task.setFinishAt(state.finishDate());
        }

        Integer coreStatus = state.status();
        Integer localStatus = task.getStatus();
        if (coreStatus == null || coreStatus.equals(localStatus)) {
            return;
        }
        task.setStatus(coreStatus);
        task.setAsyncTaskRecord(new AsyncTaskRef(task.getTaskId(), coreStatus));
        log.info("{}下载任务状态已校准: downloadId={}, taskId={}, 本地状态={}, core状态={}",
                LOG_PREFIX, task.getId(), task.getTaskId(), localStatus, coreStatus);
        try {
            if (isFinalStatus(coreStatus)) {
                // 终态：把大小/已下载/文件名/结束时间一并落库，避免任务执行实例掉线后镜像长期残缺
                downloadTaskRepo.updateFinish(task.getId(), coreStatus, task.getSize(), task.getLoaded(),
                        task.getName(), task.getFinishAt() == null ? new Date() : task.getFinishAt());
            } else {
                downloadTaskRepo.updateStatus(task.getId(), coreStatus);
            }
        } catch (RuntimeException e) {
            // 镜像回写失败不影响本次列表返回：权威状态仍在 core，下次查询会再次校准
            log.warn("{}回写下载任务状态镜像失败: downloadId={}, status={}, cause={}",
                    LOG_PREFIX, task.getId(), coreStatus, e.toString());
        }
    }

    /**
     * 判断异步任务状态是否为终态。
     *
     * @param status 状态值，可为 {@code null}
     * @return 完成 / 失败 / 取消 / 离线返回 {@code true}
     */
    private static boolean isFinalStatus(Integer status) {
        if (status == null) {
            return false;
        }
        return FINISH_STATUSES.contains(status) || FAILED_STATUSES.contains(status);
    }
}
