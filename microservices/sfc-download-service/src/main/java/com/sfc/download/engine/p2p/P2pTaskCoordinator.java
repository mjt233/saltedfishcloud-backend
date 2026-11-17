package com.sfc.download.engine.p2p;

import com.sfc.cloud.task.worker.SfcTaskDetachedException;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.config.SfcDownloadInternalProperties;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.util.DownloadFileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * P2P（磁力链 / 种子）下载任务协调器：检查点、续传接管、做种登记与生命周期收口的总编排。
 *
 * <p><b>职责边界：</b>本类不负责上传（上传仍由任务处理器调用落盘服务完成），只负责：</p>
 * <ul>
 *   <li>{@link #start(DownloadContext)}：新建任务 —— （可选）复用做种文件 → 创建任务目录 → 写
 *       {@code PREPARED} 检查点 → 提交 aria2 → {@code SUBMITTED} → 轮询（磁力链 gid 跟随）→ 采集产物 →
 *       {@code DOWNLOADED} → 登记做种 → 返回产物；</li>
 *   <li>{@link #resume(P2pTaskState, DownloadContext)}：接管任务 —— 按检查点阶段分支恢复（活动 gid 继续轮询 /
 *       已完成采集产物 / gid 丢失且目录存在时同目录 {@code continue=true} 尽力续传），接管次数超限主动失败；</li>
 *   <li>上传阶段幂等：{@link #markUploading}、{@link #markUploaded} 与 {@link #awaitUploadSettled}；</li>
 *   <li>生命周期收口矩阵：{@link #finishSuccess}、{@link #releaseOnFailure}、{@link #releaseOnCancel}；
 *       挂起（detach）时<b>不动</b> aria2 与本地目录，保留检查点等待接管。</li>
 * </ul>
 *
 * <p><b>检查点：</b>{@link P2pTaskState} JSON 经 {@link DownloadContext#saveCheckpoint(String)} 上报 core；
 * 本地目录与 aria2 目录在首次创建后固化，恢复时复用，配合强制 {@code continue=true} 实现续传。</p>
 *
 * <p><b>做种 SPI：</b>{@link P2pSeedRegistrar} 通过 {@link ObjectProvider} 懒加载，实现缺失时使用 no-op 兜底
 * （此时下载完成即清理，行为与改造前一致）。</p>
 *
 * <p><b>并发：</b>本类是 Spring 单例，一次执行的全部状态都保存在参数 {@link DownloadContext} 与
 * {@link P2pTaskState} 中，不持有跨任务可变状态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class P2pTaskCoordinator {

    /**
     * 日志前缀，与下载任务其它组件保持一致。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 任务接管次数上限：超过该次数视为异常任务（防死循环），主动上报失败并清理。
     */
    private static final int MAX_RESUME_COUNT = 50;

    /**
     * aria2 元数据伪文件的路径标记，采集既有产物时必须跳过。
     */
    private static final String METADATA_MARKER = "[METADATA]";

    /**
     * 等待上传结果时允许的最小休眠间隔（毫秒），避免配置为 0 时忙等。
     */
    private static final long MIN_WAIT_INTERVAL_MILLIS = 100L;

    /**
     * aria2 下载引擎（提交 / 轮询 / 采集 / 丢弃结果等原子能力）。
     */
    private final Aria2DownloadEngine aria2Engine;

    /**
     * aria2 共享工作区：任务目录映射与 aria2 侧目录换算。
     */
    private final Aria2Workspace aria2Workspace;

    /**
     * 做种保留 SPI（懒加载；实现缺失时用 no-op 兜底）。
     */
    private final ObjectProvider<P2pSeedRegistrar> seedRegistrarProvider;

    /**
     * 本服务内部调优配置（上传阶段陈旧阈值与等待参数）。
     */
    private final SfcDownloadInternalProperties internalProperties;

    /**
     * 读取当前任务可用的检查点。
     *
     * <p>以下情况返回 {@code null}（按「无检查点」处理，重新下载）：检查点为空、JSON 不可解析、版本不支持、
     * 或检查点记录的下载任务 id 与当前任务不一致。</p>
     *
     * @param context 执行上下文
     * @return 可恢复的检查点；没有时返回 {@code null}
     */
    public P2pTaskState loadResumable(DownloadContext context) {
        String raw = context.getCheckpoint();
        if (!SfcStringUtils.hasText(raw)) {
            return null;
        }
        Optional<P2pTaskState> parsed = P2pTaskState.fromJson(raw);
        if (parsed.isEmpty()) {
            context.log("P2P 检查点不可用，按新任务重新下载");
            return null;
        }
        P2pTaskState state = parsed.get();
        if (!state.belongsTo(context.getDownloadId())) {
            log.warn("{}P2P 检查点与当前下载任务不匹配，忽略并重新下载: checkpointDownloadId={}, currentDownloadId={}",
                    LOG_PREFIX, state.getDownloadId(), context.getDownloadId());
            context.log("检查点与当前任务不匹配，忽略并重新下载");
            return null;
        }
        return state;
    }

    /**
     * 新建 P2P 下载并完成到「产物可上传」阶段。
     *
     * <p>提交前先确保种子内容可用（URL 形式的 TORRENT 先拉取并回填 {@code torrentBase64}），再查询做种 SPI
     * 是否可复用现有文件：命中则不提交 aria2，直接把检查点置为 {@code DOWNLOADED} 并读取做种目录中的产物
     * （任务日志说明「复用做种中的文件，无需重新下载」）；未命中则正常提交，种子内容已回填可供做种登记计算
     * 指纹。</p>
     *
     * @param context 执行上下文
     * @return 检查点与产物
     */
    public P2pTaskResult start(DownloadContext context) {
        prepareTorrentContent(context, true);
        Optional<P2pSeedReuseTarget> reusable = findReusable(context);
        if (reusable.isPresent()) {
            P2pTaskResult reused = tryReuse(context, reusable.get());
            if (reused != null) {
                return reused;
            }
        }
        Path localDir = context.getTempDir();
        String aria2Dir = aria2Workspace.toAria2Dir(localDir);
        P2pTaskState state = P2pTaskState.newPrepared(context.getDownloadId(), localDir, aria2Dir);
        context.attachP2pState(state);
        context.log("创建 P2P 任务目录: " + localDir);
        log.info("{}P2P 任务开始: downloadId={}, localDir={}, aria2Dir={}",
                LOG_PREFIX, context.getDownloadId(), localDir, aria2Dir);
        saveCheckpoint(context, state);
        return downloadWithNewSession(context, state);
    }

    /**
     * 接管既有 P2P 下载任务。
     *
     * <p>接管计数 {@code resumeCount} 先 +1 并落盘；超过 {@value #MAX_RESUME_COUNT} 次视为异常任务，抛
     * 业务异常由任务处理器上报失败并按收口矩阵清理。随后按检查点阶段分支：</p>
     * <ul>
     *   <li>{@code PREPARED}：尚未提交，直接提交 aria2 开始下载；</li>
     *   <li>{@code SUBMITTED / DOWNLOADING}：探测 gid 是否仍存在——存在则重建会话继续轮询；丢失则在同一目录
     *       以 {@code continue=true} 重新提交（尽力续传）；</li>
     *   <li>{@code DOWNLOADED}：直接采集既有产物；未登记做种时补登记；</li>
     *   <li>{@code UPLOADING / UPLOADED}：上传阶段的幂等由任务处理器负责，协调器不处理。</li>
     * </ul>
     *
     * @param state   检查点（来自 {@link #loadResumable}）
     * @param context 执行上下文
     * @return 检查点与产物
     */
    public P2pTaskResult resume(P2pTaskState state, DownloadContext context) {
        context.attachP2pState(state);
        int resumeCount = state.getResumeCount() + 1;
        state.setResumeCount(resumeCount);
        saveCheckpoint(context, state);
        if (resumeCount > MAX_RESUME_COUNT) {
            context.log("任务接管次数 " + resumeCount + " 超过上限 " + MAX_RESUME_COUNT + "，任务失败");
            throw new SfcJsonException(500,
                    "P2P 任务接管次数超过上限（" + MAX_RESUME_COUNT + " 次），已停止重试");
        }
        Path localDir = state.localDirPath();
        if (localDir == null) {
            log.warn("{}检查点中的任务目录非法，按新任务重新下载: downloadId={}, localDir={}",
                    LOG_PREFIX, context.getDownloadId(), state.getLocalDir());
            context.log("检查点中的任务目录非法，按新任务重新下载");
            Path newDir = context.getTempDir();
            P2pTaskState fresh = P2pTaskState.newPrepared(context.getDownloadId(), newDir,
                    aria2Workspace.toAria2Dir(newDir));
            context.attachP2pState(fresh);
            saveCheckpoint(context, fresh);
            return downloadWithNewSession(context, fresh);
        }
        context.useTempDir(localDir);
        // 接管时同样先确保种子内容可用：后续 DOWNLOADED 阶段补登记做种需要 torrent_sha256（同种子复用键）。
        // 已下载的数据不能因种子 URL 暂不可达而失败，因此这里降级处理。
        prepareTorrentContent(context, false);
        log.info("{}接管 P2P 任务: downloadId={}, phase={}, resumeCount={}, localDir={}, gids={}",
                LOG_PREFIX, context.getDownloadId(), state.getPhase(), resumeCount, localDir, state.getGids());
        context.log("接管既有 P2P 任务: phase=" + state.getPhase()
                + (state.getGids().isEmpty() ? "" : ", gid=" + String.join(",", state.getGids())));
        return switch (state.getPhase()) {
            case PREPARED -> {
                saveCheckpoint(context, state);
                yield downloadWithNewSession(context, state);
            }
            case SUBMITTED, DOWNLOADING -> resumeDownload(context, state);
            case DOWNLOADED -> completeDownload(context, state);
            case UPLOADING, UPLOADED -> throw new SfcJsonException(500,
                    "上传阶段的 P2P 检查点应由任务处理器处理，协调器不负责恢复: phase=" + state.getPhase());
        };
    }

    /**
     * 采集既有任务目录中的产物（用于 {@code phase=UPLOADING} 接管的重新上传、以及复用做种文件场景）。
     *
     * @param state   检查点
     * @param context 执行上下文
     * @return 检查点与产物
     * @throws SfcJsonException 目录不存在或没有任何可上传文件时抛出
     */
    public P2pTaskResult collectExisting(P2pTaskState state, DownloadContext context) {
        context.attachP2pState(state);
        List<DownloadArtifact> artifacts = collectExistingArtifacts(context, state);
        if (state.isPhase(P2pTaskState.Phase.UPLOADING)) {
            context.log("接管上传阶段任务: 已采集既有产物 " + artifacts.size() + " 个，准备重新上传");
        }
        return new P2pTaskResult(state, artifacts);
    }

    /**
     * 上传阶段幂等：判断旧实例是否可能仍在上传，必要时做有界等待。
     *
     * <p>规则（契约 §3.5）：</p>
     * <ul>
     *   <li>缺少 {@code uploadStartedAt} 或已超过陈旧阈值（{@code sfc.download.resume.upload-stale-millis}，
     *       默认 10 分钟）→ 按陈旧处理，调用方重新上传；</li>
     *   <li>仍新鲜 → 最多等待 {@code upload-wait-attempts}（默认 3）次、每次
     *       {@code upload-wait-interval-millis}（默认 10 秒），期间重新检查检查点是否已变为
     *       {@code UPLOADED}；仍不是则按陈旧处理；</li>
     *   <li>等待期间收到挂起请求 → 保留检查点并抛 {@link SfcTaskDetachedException}。</li>
     * </ul>
     *
     * <p><b>已知限制：</b>Worker SDK 未暴露「重新从 core 读取检查点」的接口，
     * {@link DownloadContext#getCheckpoint()} 返回的是本实例已知的最新值，因此本方法实际只能观察本实例
     * 的写入；等待窗口主要是给「旧实例可能仍持有任务并完成上传」留出时间，超时后仍按陈旧处理。</p>
     *
     * @param context 执行上下文
     * @param state   检查点（{@code phase=UPLOADING}）
     * @return 最新的检查点（可能已变为 {@code UPLOADED}）；入参为空时返回空
     * @throws SfcTaskDetachedException 等待期间收到挂起请求时抛出
     */
    public Optional<P2pTaskState> awaitUploadSettled(DownloadContext context, P2pTaskState state) {
        if (state == null) {
            return Optional.empty();
        }
        long staleMillis = resolveUploadStaleMillis();
        long startedAt = state.getUploadStartedAt() == null ? 0L : state.getUploadStartedAt();
        long now = System.currentTimeMillis();
        if (startedAt <= 0L || now - startedAt >= staleMillis) {
            context.log("上传阶段检查点已陈旧或缺少开始时间，按陈旧处理（重新上传，可能产生重复文件）");
            log.warn("{}上传阶段检查点陈旧，按重新上传处理: downloadId={}, uploadStartedAt={}, staleMillis={}",
                    LOG_PREFIX, context.getDownloadId(), startedAt, staleMillis);
            return Optional.of(state);
        }
        int attempts = resolveUploadWaitAttempts();
        long interval = resolveUploadWaitIntervalMillis();
        context.log("接管时发现上传阶段较新（开始于 " + ((now - startedAt) / 1000L) + " 秒前），等待旧实例上传结果");
        long deadline = startedAt + staleMillis;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
                break;
            }
            sleep(context, Math.min(interval, remaining));
            if (context.isDetachRequested()) {
                throw new SfcTaskDetachedException("等待上传结果期间收到挂起请求，任务已挂起");
            }
            P2pTaskState latest = P2pTaskState.fromJson(context.getCheckpoint()).orElse(null);
            if (latest != null && latest.isUploaded()) {
                context.log("等待期间检查点已变为 UPLOADED，将直接补报完成");
                return Optional.of(latest);
            }
        }
        context.log("上传阶段检查点仍未变为 UPLOADED，按陈旧处理（重新上传，可能产生重复文件）");
        log.warn("{}上传阶段等待超时，按重新上传处理: downloadId={}", LOG_PREFIX, context.getDownloadId());
        return Optional.of(state);
    }

    /**
     * 上传前写检查点：{@code phase=UPLOADING, uploadStartedAt=now}。
     *
     * @param context 执行上下文
     * @param state   检查点，允许为 {@code null}
     */
    public void markUploading(DownloadContext context, P2pTaskState state) {
        if (state == null) {
            return;
        }
        state.setPhase(P2pTaskState.Phase.UPLOADING);
        state.setUploadStartedAt(System.currentTimeMillis());
        state.setUploaded(false);
        saveCheckpoint(context, state);
        context.log("进入上传阶段（已保存检查点）");
    }

    /**
     * 上传成功后写检查点：{@code phase=UPLOADED, uploaded=true, savedName, savedBytes}。
     *
     * <p>接管实例看到该状态时直接补报 FINISH，<b>不重复上传</b>。</p>
     *
     * @param context    执行上下文
     * @param state      检查点，允许为 {@code null}
     * @param savedName  上传后的网盘保存名
     * @param savedBytes 上传成功的字节数合计
     */
    public void markUploaded(DownloadContext context, P2pTaskState state, String savedName, long savedBytes) {
        if (state == null) {
            return;
        }
        state.setPhase(P2pTaskState.Phase.UPLOADED);
        state.setUploaded(true);
        state.setSavedName(savedName);
        state.setSavedBytes(savedBytes);
        saveCheckpoint(context, state);
        context.log("上传成功（已保存检查点）: " + savedName + "（" + savedBytes + " 字节）");
    }

    /**
     * 成功收口：按生命周期收口矩阵处理 aria2 任务结果与本地目录。
     *
     * <ul>
     *   <li>已登记做种：保留 aria2 任务（做种中）与本地目录，由做种生命周期到期清理；</li>
     *   <li>未启用做种：{@code forceRemove + removeDownloadResult} 并删除本地目录。</li>
     * </ul>
     *
     * @param context 执行上下文
     * @param state   检查点，允许为 {@code null}（非 P2P 或未产生现场）
     */
    public void finishSuccess(DownloadContext context, P2pTaskState state) {
        if (state == null) {
            return;
        }
        if (state.isSeeding()) {
            context.log("下载完成且已登记做种保留，本地目录与 aria2 任务保留至做种到期");
            return;
        }
        context.log("下载完成且未启用做种保留，清理 aria2 任务与本地目录");
        discard(state);
    }

    /**
     * 失败收口：按生命周期收口矩阵处理 aria2 任务结果与本地目录。
     *
     * <p>做种已登记时保留（做种继续，由做种生命周期到期清理）；否则移除 aria2 任务结果并删除本地目录。</p>
     *
     * @param context 执行上下文
     * @param state   检查点，允许为 {@code null}
     */
    public void releaseOnFailure(DownloadContext context, P2pTaskState state) {
        if (state == null) {
            return;
        }
        if (state.isSeeding()) {
            context.log("任务失败，但本地目录与 aria2 任务属于做种保留，保持不动");
            return;
        }
        context.log("任务失败，清理 aria2 任务与本地目录");
        discard(state);
    }

    /**
     * 取消收口：按生命周期收口矩阵处理 aria2 任务结果与本地目录。
     *
     * <p>正常下载任务直接 {@code forceRemove + removeDownloadResult} 并删除本地目录。若该任务复用了做种中的
     * 文件（{@code seeding=true} 且目录属于做种记录），则<b>不清理</b>——否则会误删其它任务的做种文件、并停止
     * 其做种；此时交由做种生命周期管理。</p>
     *
     * @param context 执行上下文
     * @param state   检查点，允许为 {@code null}
     */
    public void releaseOnCancel(DownloadContext context, P2pTaskState state) {
        if (state == null) {
            return;
        }
        if (state.isSeeding()) {
            context.log("任务取消，但本地目录与 aria2 任务属于做种保留，保持不动");
            return;
        }
        context.log("任务取消，清理 aria2 任务与本地目录");
        discard(state);
    }

    // ==================== 内部实现 ====================

    /**
     * 提交 aria2 并下载到可上传阶段（新建任务 / gid 丢失重新提交共用）。
     *
     * @param context 执行上下文
     * @param state   检查点
     * @return 检查点与产物
     */
    private P2pTaskResult downloadWithNewSession(DownloadContext context, P2pTaskState state) {
        Aria2TaskSession session;
        try {
            session = aria2Engine.submitSession(context);
        } catch (RuntimeException e) {
            saveOnFailure(context, state, null);
            throw e;
        }
        state.replaceGids(session.gids());
        state.setPhase(P2pTaskState.Phase.SUBMITTED);
        saveCheckpoint(context, state);
        return awaitAndCollect(context, state, session);
    }

    /**
     * 轮询到结束、采集产物、登记做种（统一的下载完成路径）。
     *
     * @param context 执行上下文
     * @param state   检查点
     * @param session aria2 会话
     * @return 检查点与产物
     */
    private P2pTaskResult awaitAndCollect(DownloadContext context, P2pTaskState state, Aria2TaskSession session) {
        Map<String, Object> status;
        List<DownloadArtifact> artifacts;
        try {
            status = aria2Engine.awaitCompletion(context, session,
                    changed -> syncSession(context, state, changed));
            artifacts = aria2Engine.collectArtifacts(context, status);
        } catch (SfcTaskDetachedException e) {
            saveDetached(context, state, session);
            throw e;
        } catch (RuntimeException e) {
            saveOnFailure(context, state, session);
            throw e;
        }
        syncSession(context, state, session);
        state.setPhase(P2pTaskState.Phase.DOWNLOADED);
        saveCheckpoint(context, state);
        // 下载完成后再兜底确保一次种子内容（接管时首次拉取失败/未触发时，做种登记仍需 torrent_sha256）；
        // 已回填时 ensure 直接返回，不产生额外网络请求
        prepareTorrentContent(context, false);
        registerSeeding(context, state, artifacts);
        saveCheckpoint(context, state);
        return new P2pTaskResult(state, artifacts);
    }

    /**
     * 接管下载阶段：探测活动 gid，活动则继续轮询；全部丢失则在同目录以 {@code continue=true} 重新提交。
     *
     * @param context 执行上下文
     * @param state   检查点（{@code phase=SUBMITTED/DOWNLOADING}）
     * @return 检查点与产物
     */
    private P2pTaskResult resumeDownload(DownloadContext context, P2pTaskState state) {
        List<String> trackedGids = new ArrayList<>(state.getGids());
        List<String> aliveGids;
        try {
            aliveGids = aria2Engine.filterAliveGids(trackedGids);
        } catch (RuntimeException e) {
            throw new SfcJsonException(502, "aria2 不可达，无法恢复 P2P 下载任务: " + describe(e));
        }
        if (aliveGids.isEmpty()) {
            log.warn("{}检查点中的 aria2 gid 均已丢失，尝试在同一目录续传: downloadId={}, localDir={}",
                    LOG_PREFIX, context.getDownloadId(), state.getLocalDir());
            context.log("未找到可恢复的 aria2 任务（gid 已丢失），尝试在同一目录使用 continue=true 继续下载");
            ensureLocalDir(state);
            state.replaceGids(List.of());
            state.replaceContentGids(List.of());
            state.setPhase(P2pTaskState.Phase.PREPARED);
            saveCheckpoint(context, state);
            return downloadWithNewSession(context, state);
        }
        context.log("已接管 aria2 任务: gid=" + String.join(",", aliveGids));
        Aria2TaskSession session;
        try {
            session = aria2Engine.resumeSession(context, aliveGids);
        } catch (RuntimeException e) {
            saveOnFailure(context, state, null);
            throw e;
        }
        return awaitAndCollect(context, state, session);
    }

    /**
     * 接管「下载已完成」阶段：采集既有产物，必要时补登记做种；目录丢失时降级为重新下载。
     *
     * @param context 执行上下文
     * @param state   检查点（{@code phase=DOWNLOADED}）
     * @return 检查点与产物
     */
    private P2pTaskResult completeDownload(DownloadContext context, P2pTaskState state) {
        Path localDir = state.localDirPath();
        if (localDir == null || !Files.isDirectory(localDir)) {
            log.warn("{}检查点记录的任务目录不存在，重新下载: downloadId={}, localDir={}",
                    LOG_PREFIX, context.getDownloadId(), state.getLocalDir());
            context.log("检查点记录的任务目录不存在，重新下载");
            state.replaceGids(List.of());
            state.replaceContentGids(List.of());
            state.setPhase(P2pTaskState.Phase.PREPARED);
            saveCheckpoint(context, state);
            return downloadWithNewSession(context, state);
        }
        List<DownloadArtifact> artifacts = collectExistingArtifacts(context, state);
        if (!state.isSeeding()) {
            registerSeeding(context, state, artifacts);
        }
        saveCheckpoint(context, state);
        return new P2pTaskResult(state, artifacts);
    }

    /**
     * 登记做种保留（SPI 实现缺失时按「不做种」处理）。
     *
     * <p>登记异常不吞掉：直接失败并由任务处理器按收口矩阵清理——没有做种记录的文件无法被做种生命周期
     * 到期清理，静默按「无做种」处理会留下永久残留。</p>
     *
     * @param context   执行上下文
     * @param state     检查点
     * @param artifacts 下载产物
     */
    private void registerSeeding(DownloadContext context, P2pTaskState state, List<DownloadArtifact> artifacts) {
        P2pSeedRegistrar registrar = registrar();
        boolean registered;
        try {
            registered = registrar.register(context, state, artifacts);
        } catch (RuntimeException e) {
            log.warn("{}登记做种保留失败: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), describe(e));
            context.log("登记做种保留失败: " + describe(e));
            throw new SfcJsonException(500, "登记做种保留失败: " + describe(e));
        }
        state.setSeeding(registered);
        if (registered) {
            context.log("已登记做种保留，本地文件保留做种");
            log.info("{}P2P 下载完成并登记做种保留: downloadId={}, seedTaskId={}, seedEndAt={}",
                    LOG_PREFIX, context.getDownloadId(), state.getSeedTaskId(), state.getSeedEndAt());
        } else {
            context.log("未启用做种保留（或做种 SPI 不可用），下载完成后将清理本地文件");
        }
    }

    /**
     * 预取 URL 形式种子的内容（回填 {@link com.sfc.download.model.DownloadTaskParams#torrentBase64}）。
     *
     * <p>只对 TORRENT 生效；MAGNET 无需种子内容。回填后同种子复用查询（{@code torrent_sha256}）与做种登记
     * 指纹都有内容可用。客户端直传 Base64 的场景由引擎侧直接跳过、不会重复拉取。</p>
     *
     * <p>拉取失败时的语义由 {@code fatal} 控制：<b>新建任务</b>必须失败（没有种子内容无法提交 aria2）；
     * <b>接管任务</b>尽量降级（已下载的数据不应因种子 URL 暂不可达而被清理/失败，最多缺失
     * {@code torrent_sha256} 复用键）。</p>
     *
     * @param context 执行上下文
     * @param fatal   拉取失败时是否向上抛出异常
     * @throws SfcJsonException {@code fatal=true} 且种子地址为空、拉取失败、内容为空或超过体积上限时抛出
     */
    private void prepareTorrentContent(DownloadContext context, boolean fatal) {
        if (context.getParams().type != DownloadType.TORRENT) {
            return;
        }
        boolean alreadyAvailable = SfcStringUtils.hasText(context.getParams().torrentBase64);
        try {
            aria2Engine.ensureTorrentBase64(context);
        } catch (RuntimeException e) {
            if (fatal) {
                throw e;
            }
            log.warn("{}获取种子文件内容失败，本次接管降级继续（做种指纹可能缺失）: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), describe(e));
            context.log("获取种子文件内容失败，降级继续: " + describe(e));
            return;
        }
        if (!alreadyAvailable && SfcStringUtils.hasText(context.getParams().torrentBase64)) {
            log.info("{}已获取种子文件内容（用于同种子复用匹配与做种指纹）: downloadId={}",
                    LOG_PREFIX, context.getDownloadId());
        }
    }

    /**
     * 查询可复用的做种文件（SPI 实现缺失或查询失败时降级为正常下载）。
     *
     * @param context 执行上下文
     * @return 可复用目标；未命中或查询失败时返回空
     */
    private Optional<P2pSeedReuseTarget> findReusable(DownloadContext context) {
        try {
            return registrar().findReusable(context.getParams());
        } catch (RuntimeException e) {
            log.warn("{}查询可复用做种文件失败，按正常下载处理: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), describe(e));
            context.log("查询可复用做种文件失败，按正常下载处理: " + describe(e));
            return Optional.empty();
        }
    }

    /**
     * 尝试复用做种中的文件（不提交 aria2，直接读取既有目录产物）。
     *
     * @param context 执行上下文
     * @param target  可复用目标
     * @return 复用成功时的结果；目标不可用时返回 {@code null}（调用方降级为正常下载）
     */
    private P2pTaskResult tryReuse(DownloadContext context, P2pSeedReuseTarget target) {
        Path localDir;
        try {
            localDir = Path.of(Objects.requireNonNull(target.localDir(), "复用目标缺少本地目录"))
                    .toAbsolutePath().normalize();
        } catch (InvalidPathException | NullPointerException e) {
            log.warn("{}做种复用目标的本地目录非法，降级为正常下载: localDir={}, cause={}",
                    LOG_PREFIX, target.localDir(), describe(e));
            return null;
        }
        if (!Files.isDirectory(localDir)) {
            log.warn("{}做种复用目标的本地目录不存在，降级为正常下载: localDir={}", LOG_PREFIX, localDir);
            return null;
        }
        List<DownloadArtifact> artifacts = scanArtifacts(localDir);
        if (artifacts.isEmpty()) {
            log.warn("{}做种复用目标目录中没有可上传文件，降级为正常下载: localDir={}", LOG_PREFIX, localDir);
            return null;
        }
        P2pTaskState state = new P2pTaskState();
        state.setDownloadId(context.getDownloadId());
        state.setLocalDir(localDir.toString());
        state.setAria2Dir(target.aria2Dir());
        state.setPhase(P2pTaskState.Phase.DOWNLOADED);
        state.setSeeding(true);
        state.setSeedTaskId(target.seedTaskId());
        state.setSeedEndAt(target.seedEndAt() == null ? null : target.seedEndAt().getTime());
        if (SfcStringUtils.hasText(target.contentGid())) {
            state.replaceGids(List.of(target.contentGid()));
            state.replaceContentGids(List.of(target.contentGid()));
        }
        long now = System.currentTimeMillis();
        state.setSubmittedAt(now);
        state.touch();
        context.useTempDir(localDir);
        context.attachP2pState(state);
        saveCheckpoint(context, state);
        context.log("复用做种中的文件，无需重新下载（做种保留至 "
                + (target.seedEndAt() == null ? "未知" : target.seedEndAt()) + "）");
        log.info("{}复用做种中的文件，无需重新下载: downloadId={}, seedTaskId={}, localDir={}, 文件数={}",
                LOG_PREFIX, context.getDownloadId(), target.seedTaskId(), localDir, artifacts.size());
        return new P2pTaskResult(state, artifacts);
    }

    /**
     * 采集既有任务目录中的两个及以上级别产物（不存在时报错）。
     *
     * @param context 执行上下文
     * @param state   检查点
     * @return 产物列表（至少一个文件）
     * @throws SfcJsonException 目录不存在或目录中没有可上传文件时抛出
     */
    private List<DownloadArtifact> collectExistingArtifacts(DownloadContext context, P2pTaskState state) {
        Path localDir = state.localDirPath();
        if (localDir == null) {
            throw new SfcJsonException(500, "P2P 检查点缺少任务目录，无法采集既有产物");
        }
        if (!Files.isDirectory(localDir)) {
            throw new SfcJsonException(502, "P2P 任务目录不存在，无法上传产物: " + localDir);
        }
        context.useTempDir(localDir);
        List<DownloadArtifact> artifacts = scanArtifacts(localDir);
        if (artifacts.isEmpty()) {
            throw new SfcJsonException(502, "P2P 任务目录中没有可上传的文件: " + localDir);
        }
        context.log("已采集既有任务目录产物: " + localDir + "，文件数 " + artifacts.size());
        return artifacts;
    }

    /**
     * 递归扫描目录中的文件产物。
     *
     * <p>跳过 aria2 的 {@code [METADATA]} 伪文件；产物相对路径基于给定目录计算（与
     * {@link DownloadArtifact#of(Path, Path)} 的契约一致），按相对路径排序保证结果稳定。</p>
     *
     * @param root 任务目录
     * @return 产物列表；目录为空时返回空列表
     * @throws SfcJsonException 读取目录失败时抛出
     */
    private static List<DownloadArtifact> scanArtifacts(Path root) {
        List<DownloadArtifact> artifacts = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isMetadataPseudoFile(root.relativize(path)))
                    .sorted(Comparator.comparing(path -> posixRelative(root, path)))
                    .forEach(path -> {
                        try {
                            artifacts.add(DownloadArtifact.of(root, path));
                        } catch (IllegalArgumentException | UncheckedIOException e) {
                            log.warn("{}跳过不可用的本地产物: path={}, cause={}",
                                    LOG_PREFIX, path, describe(e));
                        }
                    });
        } catch (IOException e) {
            throw new SfcJsonException(500, "读取 P2P 任务目录失败: " + describe(e));
        }
        return artifacts;
    }

    /**
     * 判断相对路径是否指向 aria2 的元数据伪文件。
     *
     * @param relative 相对任务目录的路径
     * @return 是元数据伪文件返回 {@code true}
     */
    private static boolean isMetadataPseudoFile(Path relative) {
        for (Path segment : relative) {
            if (segment.toString().contains(METADATA_MARKER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把相对路径转换为 POSIX 风格文本（用于稳定排序）。
     *
     * @param root 任务目录
     * @param path 任务目录下的路径
     * @return POSIX 风格相对路径
     */
    private static String posixRelative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    /**
     * 确保检查点记录的任务目录存在（gid 丢失后同目录续传前调用）。
     *
     * @param state 检查点
     * @throws SfcJsonException 目录创建失败或缺少目录记录时抛出
     */
    private static void ensureLocalDir(P2pTaskState state) {
        Path localDir = state.localDirPath();
        if (localDir == null) {
            throw new SfcJsonException(500, "P2P 检查点缺少任务目录，无法续传");
        }
        try {
            Files.createDirectories(localDir);
        } catch (IOException e) {
            throw new SfcJsonException(500, "创建 P2P 任务目录失败: " + describe(e));
        }
    }

    /**
     * 把会话的最新状态同步回检查点并落盘。
     *
     * @param context 执行上下文
     * @param state   检查点
     * @param session aria2 会话
     */
    private void syncSession(DownloadContext context, P2pTaskState state, Aria2TaskSession session) {
        if (state == null || session == null) {
            return;
        }
        state.replaceGids(session.gids());
        state.replaceContentGids(session.contentGids());
        if (SfcStringUtils.hasText(session.infohash())) {
            state.setInfohash(session.infohash());
        }
        if (state.getPhase() == null || state.isPhase(P2pTaskState.Phase.PREPARED)
                || state.getPhase().isDownloading()) {
            state.setPhase(P2pTaskState.Phase.DOWNLOADING);
        }
        saveCheckpoint(context, state);
    }

    /**
     * 挂起时保存检查点（aria2 与本地目录保持不动，等待接管）。
     *
     * @param context 执行上下文
     * @param state   检查点
     * @param session 当前会话，允许为 {@code null}
     */
    private void saveDetached(DownloadContext context, P2pTaskState state, Aria2TaskSession session) {
        if (session != null) {
            syncSession(context, state, session);
        } else {
            saveCheckpoint(context, state);
        }
        context.log("收到挂起请求，已保存检查点（phase=" + state.getPhase() + "），等待接管");
    }

    /**
     * 失败的异常传播路径上尽力保存检查点（失败清理由任务处理器按收口矩阵执行）。
     *
     * @param context 执行上下文
     * @param state   检查点
     * @param session 当前会话，允许为 {@code null}
     */
    private void saveOnFailure(DownloadContext context, P2pTaskState state, Aria2TaskSession session) {
        try {
            if (session != null) {
                syncSession(context, state, session);
            } else {
                saveCheckpoint(context, state);
            }
        } catch (RuntimeException e) {
            log.warn("{}失败收尾时保存检查点失败（忽略）: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), describe(e));
        }
    }

    /**
     * 保存检查点（序列化/上报失败只记录日志，不打断任务；SDK 会对待补发检查点做周期重试）。
     *
     * @param context 执行上下文
     * @param state   检查点
     */
    private void saveCheckpoint(DownloadContext context, P2pTaskState state) {
        if (state == null) {
            return;
        }
        state.touch();
        try {
            context.saveCheckpoint(state.toJson());
        } catch (RuntimeException e) {
            log.warn("{}保存 P2P 检查点失败: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), describe(e));
            context.log("保存检查点失败: " + describe(e));
        }
    }

    /**
     * 按收口矩阵清理 aria2 任务结果与本地目录（best-effort）。
     *
     * @param state 检查点
     */
    private void discard(P2pTaskState state) {
        try {
            aria2Engine.discardTaskResults(state.getGids());
        } catch (RuntimeException e) {
            log.warn("{}清理 aria2 任务结果失败（忽略）: downloadId={}, gids={}, cause={}",
                    LOG_PREFIX, state.getDownloadId(), state.getGids(), describe(e));
        }
        DownloadFileUtils.deleteQuietly(state.localDirPath());
    }

    /**
     * 获取做种 SPI 实现（缺失时返回 no-op）。
     *
     * @return 做种 SPI，永不为 {@code null}
     */
    private P2pSeedRegistrar registrar() {
        if (seedRegistrarProvider == null) {
            return NoopP2pSeedRegistrar.INSTANCE;
        }
        P2pSeedRegistrar registrar = seedRegistrarProvider.getIfAvailable(() -> NoopP2pSeedRegistrar.INSTANCE);
        return registrar == null ? NoopP2pSeedRegistrar.INSTANCE : registrar;
    }

    /**
     * 解析上传阶段陈旧阈值（毫秒）。
     *
     * @return 陈旧阈值；配置读取失败时使用默认值
     */
    private long resolveUploadStaleMillis() {
        try {
            return internalProperties.getResume().getUploadStaleMillis();
        } catch (RuntimeException e) {
            return SfcDownloadInternalProperties.Resume.DEFAULT_UPLOAD_STALE_MILLIS;
        }
    }

    /**
     * 解析等待上传结果的单次间隔（毫秒，至少 {@value #MIN_WAIT_INTERVAL_MILLIS}）。
     *
     * @return 等待间隔
     */
    private long resolveUploadWaitIntervalMillis() {
        try {
            return Math.max(MIN_WAIT_INTERVAL_MILLIS,
                    internalProperties.getResume().getUploadWaitIntervalMillis());
        } catch (RuntimeException e) {
            return SfcDownloadInternalProperties.Resume.DEFAULT_UPLOAD_WAIT_INTERVAL_MILLIS;
        }
    }

    /**
     * 解析等待上传结果的最大次数（至少 1 次）。
     *
     * @return 等待次数
     */
    private int resolveUploadWaitAttempts() {
        try {
            return Math.max(1, internalProperties.getResume().getUploadWaitAttempts());
        } catch (RuntimeException e) {
            return SfcDownloadInternalProperties.Resume.DEFAULT_UPLOAD_WAIT_ATTEMPTS;
        }
    }

    /**
     * 可中断的休眠。
     *
     * @param context 执行上下文
     * @param millis  休眠毫秒数
     * @throws SfcTaskInterruptedException 休眠期间线程被中断且任务已取消时抛出
     */
    private static void sleep(DownloadContext context, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.checkInterrupted();
            throw new SfcTaskInterruptedException("等待 P2P 上传结果被中断", e);
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
     * P2P 任务协调结果。
     *
     * @param state     任务检查点（含做种登记信息）
     * @param artifacts 可供上传的本地产物列表
     */
    public record P2pTaskResult(P2pTaskState state, List<DownloadArtifact> artifacts) {
    }
}
