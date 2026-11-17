package com.sfc.download.engine.p2p;

import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.config.SfcDownloadInternalProperties;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.model.po.DownloadTaskInfo;
import com.sfc.download.repo.DownloadTaskRepo;
import com.sfc.download.util.DownloadFileUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * aria2 孤儿任务对账器。
 *
 * <p><b>要解决的问题：</b>aria2 是独立守护进程，下载服务停机/重启期间它仍会继续下载。任务被取消、
 * 失败或已经完成并由做种生命周期清理后，aria2 侧可能残留 active 下载结果与工作区目录；长期运行会
 * 累积磁盘占用与 RPC 内存。</p>
 *
 * <p><b>安全前提（必须遵守）：</b>只处理 {@code dir} 映射后位于<b>本服务任务工作区</b>
 * （{@code <shared-dir>/offline-download/}，未启用共享目录时为 JVM 临时目录 {@code offline-download/}）
 * 下的 aria2 任务；<b>绝不触碰</b>工作区之外的 aria2 任务（可能属于其它部署或其它实例）。</p>
 *
 * <p><b>决策规则：</b></p>
 * <ol>
 *   <li>从 {@code dir} 目录名解析 {@code <downloadId>-}；解析失败或不在工作区 → 跳过；</li>
 *   <li>查本服务 {@code download_task} 记录：</li>
 *   <li>记录<b>不存在</b> → 只记 WARN，<b>不删除</b>（避免误删其它部署/实例的数据）；</li>
 *   <li>状态为运行中 / 等待中（可能等待接管）→ 跳过；</li>
 *   <li>状态为完成 / 失败 / 取消且<b>不是</b>做种保护目录 → 清理
 *       （{@code forceRemove} + {@code removeDownloadResult} + 删除本地目录）；</li>
 *   <li>做种保护判断查询失败 → 跳过该候选（fail-safe，宁可残留也不误删）。</li>
 * </ol>
 *
 * <p><b>触发方式：</b>应用启动完成后执行一次；随后按
 * {@code sfc.download.aria2.reconcile-interval-millis}（默认 300000，{@code <= 0} 关闭）周期执行。
 * 这里使用自带单线程调度器而非 {@code @Scheduled}：一方面 {@code 0 = 关闭} 无法用 {@code fixedDelay}
 * 表达，另一方面避免与做种生命周期的 {@code @EnableScheduling} 产生配置耦合。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Aria2OrphanReconciler {

    /**
     * 日志前缀，与下载任务其它组件保持一致。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 对账时向 aria2 请求的状态字段：只需要 gid、状态与下载目录。
     */
    private static final List<String> STATUS_KEYS = List.of("gid", "status", "dir");

    /**
     * 单次 {@code tellWaiting} / {@code tellStopped} 查询的最大条数。
     */
    private static final int PAGE_SIZE = 1000;

    /**
     * aria2 JSON-RPC 客户端。
     */
    private final Aria2Client aria2Client;

    /**
     * aria2 共享工作区：提供路径映射、工作区归属判断与 downloadId 解析。
     */
    private final Aria2Workspace aria2Workspace;

    /**
     * 下载任务仓储：按 id 查询任务状态镜像。
     */
    private final DownloadTaskRepo downloadTaskRepo;

    /**
     * 做种保留 SPI（懒加载）：判断目录是否处于做种保护中。
     */
    private final ObjectProvider<P2pSeedRegistrar> seedRegistrarProvider;

    /**
     * 本服务的内部调优配置（提供对账周期）。
     */
    private final SfcDownloadInternalProperties internalProperties;

    /**
     * 周期调度器（仅在对账开启时创建）。
     */
    private ScheduledExecutorService scheduler;

    /**
     * 单轮对账执行标记，避免周期任务与启动任务并发执行。
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 应用启动完成后：先对账一次，再按配置周期调度。
     *
     * <p>对账周期 {@code <= 0} 时完全关闭（启动一次也不执行）。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        long interval = reconcileIntervalMillis();
        if (interval <= 0) {
            log.info("{}aria2 孤儿任务对账已关闭（{}<=0）",
                    LOG_PREFIX, "sfc.download.aria2.reconcile-interval-millis");
            return;
        }
        log.info("{}aria2 孤儿任务对账已启用: 启动后执行一次，周期={}ms", LOG_PREFIX, interval);
        runOnceSafely();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "aria2-orphan-reconciler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::runOnceSafely, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止周期调度（应用关闭时）。
     */
    @PreDestroy
    public void shutdown() {
        ScheduledExecutorService current = scheduler;
        if (current != null) {
            current.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * 执行单轮对账。
     *
     * <p>本方法可被调度器调用，也可被运维/测试直接调用。所有异常都在内部消化（只记录日志），
     * 不会向调度器抛出导致周期任务被取消。</p>
     *
     * @return 本轮对账摘要，永不为 {@code null}
     */
    public ReconcileSummary reconcileOnce() {
        String invalid = aria2Workspace.validate();
        if (SfcStringUtils.hasText(invalid)) {
            log.debug("{}aria2 共享目录配置无效，跳过本轮孤儿任务对账: {}", LOG_PREFIX, invalid);
            return ReconcileSummary.notExecuted("共享目录配置无效: " + invalid);
        }
        Map<String, Map<String, Object>> candidates;
        try {
            candidates = collectCandidates();
        } catch (RuntimeException e) {
            // aria2 不可达属于常见部署状态（未启用 P2P / aria2 未启动），只 WARN 并跳过本轮
            log.warn("{}aria2 不可达或查询失败，跳过本轮孤儿任务对账: cause={}", LOG_PREFIX, describe(e));
            return ReconcileSummary.notExecuted("aria2 查询失败: " + describe(e));
        }

        int cleaned = 0;
        int skipped = 0;
        for (Map.Entry<String, Map<String, Object>> entry : candidates.entrySet()) {
            String gid = entry.getKey();
            Map<String, Object> status = entry.getValue();
            try {
                if (reconcileCandidate(gid, status)) {
                    cleaned++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                skipped++;
                log.warn("{}处理 aria2 孤儿任务候选失败，跳过: gid={}, cause={}", LOG_PREFIX, gid, describe(e));
            }
        }
        if (cleaned > 0 || skipped > 0) {
            log.info("{}aria2 孤儿任务对账完成: 候选={}, 清理={}, 跳过={}",
                    LOG_PREFIX, candidates.size(), cleaned, skipped);
        }
        return new ReconcileSummary(true, candidates.size(), cleaned, skipped, null);
    }

    /**
     * 汇总 aria2 侧的全部任务状态（active + waiting + stopped），按 gid 去重。
     *
     * @return gid → 状态快照（保持 aria2 返回顺序）
     * @throws Aria2RpcException aria2 不可达或返回格式非法时抛出
     */
    private Map<String, Map<String, Object>> collectCandidates() {
        Map<String, Map<String, Object>> candidates = new LinkedHashMap<>();
        List<Map<String, Object>> statuses = new ArrayList<>();
        statuses.addAll(aria2Client.tellActive(STATUS_KEYS));
        statuses.addAll(aria2Client.tellWaiting(0, PAGE_SIZE, STATUS_KEYS));
        statuses.addAll(aria2Client.tellStopped(0, PAGE_SIZE, STATUS_KEYS));
        for (Map<String, Object> status : statuses) {
            String gid = text(status.get("gid"));
            if (!SfcStringUtils.hasText(gid)) {
                continue;
            }
            candidates.putIfAbsent(gid.trim(), status);
        }
        return candidates;
    }

    /**
     * 处理单个候选 gid。
     *
     * @param gid    aria2 任务标识
     * @param status aria2 状态快照
     * @return 执行了清理返回 {@code true}；跳过返回 {@code false}
     */
    private boolean reconcileCandidate(String gid, Map<String, Object> status) {
        String aria2Dir = text(status.get("dir"));
        if (!SfcStringUtils.hasText(aria2Dir)) {
            // 例如 HTTP/FTP 任务没有 dir 语义，与本服务无关
            return false;
        }
        Path localDir = aria2Workspace.toLocalDir(aria2Dir);
        if (localDir == null || !aria2Workspace.isInWorkspace(localDir)) {
            // 工作区之外：可能是其它部署/实例的 aria2 任务，绝不触碰
            return false;
        }
        Long downloadId = aria2Workspace.parseDownloadId(localDir);
        if (downloadId == null) {
            log.debug("{}无法从 aria2 任务目录解析下载任务 id，跳过: gid={}, dir={}", LOG_PREFIX, gid, aria2Dir);
            return false;
        }
        DownloadTaskInfo task = downloadTaskRepo.findById(downloadId).orElse(null);
        if (task == null) {
            log.warn("{}aria2 孤儿任务对账发现 download_task 记录不存在，仅告警不删除: downloadId={}, gid={}, dir={}",
                    LOG_PREFIX, downloadId, gid, aria2Dir);
            return false;
        }
        Integer taskStatus = task.getStatus();
        if (Objects.equals(taskStatus, DownloadTaskConstants.Status.WAITING)
                || Objects.equals(taskStatus, DownloadTaskConstants.Status.RUNNING)) {
            // 可能等待其它实例接管，必须保留
            return false;
        }
        if (!isTerminal(taskStatus)) {
            // 未知状态（例如执行节点离线）保守跳过
            return false;
        }
        if (isProtected(localDir)) {
            log.debug("{}跳过做种保护中的 aria2 任务: downloadId={}, gid={}, dir={}", LOG_PREFIX, downloadId, gid, localDir);
            return false;
        }
        log.info("{}清理 aria2 孤儿任务: downloadId={}, gid={}, status={}, aria2Dir={}, localDir={}",
                LOG_PREFIX, downloadId, gid, taskStatus, aria2Dir, localDir);
        aria2Client.forceRemove(gid);
        aria2Client.removeDownloadResult(gid);
        DownloadFileUtils.deleteQuietly(localDir);
        return true;
    }

    /**
     * 判断下载任务状态是否为终态（完成 / 失败 / 取消）。
     *
     * @param status 下载任务状态镜像，允许为 {@code null}
     * @return 属于终态返回 {@code true}
     */
    private static boolean isTerminal(Integer status) {
        return Objects.equals(status, DownloadTaskConstants.Status.FINISH)
                || Objects.equals(status, DownloadTaskConstants.Status.FAILED)
                || Objects.equals(status, DownloadTaskConstants.Status.CANCEL);
    }

    /**
     * 判断目录是否处于做种保护中。
     *
     * <p>做种实现缺失时按「不保护」处理；查询异常直接向上抛出，由调用方跳过该候选（fail-safe）。</p>
     *
     * @param localDir 本服务侧任务目录
     * @return 受做种保护返回 {@code true}
     */
    private boolean isProtected(Path localDir) {
        P2pSeedRegistrar registrar = seedRegistrarProvider == null
                ? NoopP2pSeedRegistrar.INSTANCE
                : seedRegistrarProvider.getIfAvailable(() -> NoopP2pSeedRegistrar.INSTANCE);
        return registrar != null && registrar.isProtectedDirectory(localDir);
    }

    /**
     * 安全执行单轮对账：内部吞掉所有异常，避免调度器因一次失败停止后续周期。
     */
    private void runOnceSafely() {
        if (!running.compareAndSet(false, true)) {
            log.debug("{}上一轮 aria2 孤儿任务对账尚未结束，跳过本次触发", LOG_PREFIX);
            return;
        }
        try {
            reconcileOnce();
        } catch (RuntimeException e) {
            log.warn("{}aria2 孤儿任务对账执行异常，等待下一周期重试: cause={}", LOG_PREFIX, describe(e));
        } finally {
            running.set(false);
        }
    }

    /**
     * 获取配置的对账周期（毫秒）。
     *
     * @return 对账周期；配置读取失败时使用默认值
     */
    private long reconcileIntervalMillis() {
        try {
            return internalProperties.getAria2().getReconcileIntervalMillis();
        } catch (RuntimeException e) {
            return SfcDownloadInternalProperties.Aria2.DEFAULT_RECONCILE_INTERVAL_MILLIS;
        }
    }

    /**
     * 把 aria2 状态字段宽松转为字符串。
     *
     * @param value 原始值
     * @return 字符串值；入参为 {@code null} 时返回 {@code null}
     */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
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
        return SfcStringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }

    /**
     * 单轮对账摘要。
     *
     * @param executed    本轮是否真正执行（配置无效 / aria2 不可达时为 {@code false}）
     * @param candidates  候选 aria2 任务数
     * @param cleaned     执行清理的任务数
     * @param skipped     跳过（安全保护）的任务数
     * @param skipReason  未执行原因；执行时为 {@code null}
     */
    public record ReconcileSummary(boolean executed,
                                   int candidates,
                                   int cleaned,
                                   int skipped,
                                   String skipReason) {

        /**
         * 构造「本轮未执行」的摘要。
         *
         * @param reason 未执行原因
         * @return 未执行摘要
         */
        public static ReconcileSummary notExecuted(String reason) {
            return new ReconcileSummary(false, 0, 0, 0, reason);
        }
    }
}
