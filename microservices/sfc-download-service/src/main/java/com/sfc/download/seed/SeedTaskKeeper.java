package com.sfc.download.seed;

import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.p2p.Aria2Client;
import com.sfc.download.model.po.DownloadSeedTask;
import com.sfc.download.repo.DownloadSeedTaskRepo;
import com.sfc.download.util.DownloadFileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 做种保留清理调度：周期扫描 {@code download_seed_task}，把到期的做种记录收尾。
 *
 * <p><b>单轮算法（契约 §4.3）：</b></p>
 * <ol>
 *   <li>探测 aria2 是否可达：不可达（aria2 RPC 异常等运行时异常）时只记 WARN 并跳过本轮，
 *       <b>不置终态</b>，下轮重试；</li>
 *   <li>回收「清理中」超时记录：{@code status=1} 且 {@code update_at} 早于「当前时间 - 10 分钟」的记录，
 *       视为上次清理中途崩溃，重新执行一次幂等清理；</li>
 *   <li>到期记录：{@code status=0 AND seed_end_at <= now}，逐条原子抢占（{@code status 0 → 1}），
 *       抢不到说明已被其它实例处理，直接跳过；</li>
 *   <li>清理动作：{@code aria2.forceRemove}（若仍 active）→ {@code aria2.removeDownloadResult} →
 *       {@code DownloadFileUtils.deleteQuietly(localDir)}；</li>
 *   <li>成功置 {@code status=2, finish_at=now, message=...}；本地目录删除后仍存在等异常置
 *       {@code status=3} 并记录原因。</li>
 * </ol>
 *
 * <p><b>aria2 失败与本地删除互不阻塞：</b>单条记录的 aria2 移除失败（例如 gid 已不存在）不会阻止本地目录
 * 删除，原因会拼进 {@code message}；只有「本轮开始时的可达性探测」失败才会整体跳过，避免在 aria2 短暂
 * 不可达期间误删仍在做种的目录。</p>
 *
 * <p><b>触发方式：</b>固定延迟轮询（{@code sfc.download.seed.check-interval-millis}，默认 60000 毫秒）+
 * 应用启动完成后补扫一次（{@link ApplicationReadyEvent}，在虚拟线程执行，避免拖慢应用就绪）。</p>
 *
 * <p><b>并发：</b>同一实例内用 {@link AtomicBoolean} 防止上一轮未结束时重入；跨实例的并发由「抢占」这一步
 * 的原子更新保证同一记录同一时刻只会被一个实例清理。</p>
 *
 * @see SeedTaskStatus
 * @see DownloadSeedTaskRepo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedTaskKeeper {

    /**
     * 日志前缀，与下载服务其它组件保持一致。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 单轮单类查询最多处理的做种记录数，避免单轮扫描耗时过长、占用调度线程。
     */
    static final int MAX_BATCH_SIZE = 200;

    /**
     * 「清理中」记录的超时重试时间（毫秒）：超过该时长仍未进入终态，视为上次清理中途崩溃。
     */
    static final long CLEANING_RETRY_MILLIS = 10 * 60 * 1000L;

    /**
     * {@code message} 列允许的最大字符数（列定义 1024，此处留出 "..." 截断标记的余量）。
     */
    private static final int MAX_MESSAGE_LENGTH = 1000;

    /**
     * 清理成功时写入的统一终态信息（逐条记录额外告警会拼接在其后）。
     */
    private static final String CLEANED_MESSAGE = "做种到期，已停止做种并删除本地文件";

    /**
     * 做种记录仓储（到期查询、抢占与终态回写）。
     */
    private final DownloadSeedTaskRepo seedTaskRepo;

    /**
     * aria2 JSON-RPC 客户端（停止做种、清理结果记录）。
     */
    private final Aria2Client aria2Client;

    /**
     * 本实例是否正在执行一轮扫描（防止启动补扫与定时任务重入）。
     */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /**
     * 应用启动完成后补扫一次。
     *
     * <p>清理可能包含 aria2 RPC 与目录删除等阻塞操作，因此放到虚拟线程执行，不拖慢应用就绪与首次请求。
     * 异常全部在 {@link #scanQuietly()} 内收敛，不影响启动结果。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("{}应用启动完成，提交做种保留补扫", LOG_PREFIX);
        Thread.startVirtualThread(this::scanQuietly);
    }

    /**
     * 定时扫描入口（固定延迟：上一轮结束后再等待配置间隔）。
     *
     * <p>间隔由 {@code sfc.download.seed.check-interval-millis} 控制，默认 60000 毫秒；异常不会向外抛出，
     * 保证调度器不会被单轮失败打断。</p>
     */
    @Scheduled(fixedDelayString = "${sfc.download.seed.check-interval-millis:60000}")
    public void scheduledScan() {
        scanQuietly();
    }

    /**
     * 执行一轮扫描并吞掉所有异常（只记 WARN），供定时任务与启动补扫复用。
     */
    void scanQuietly() {
        if (!scanning.compareAndSet(false, true)) {
            log.info("{}上一轮做种清理尚未结束，跳过本轮", LOG_PREFIX);
            return;
        }
        try {
            scan(new Date());
        } catch (RuntimeException e) {
            log.warn("{}做种清理本轮扫描异常，已跳过（下轮重试）: cause={}", LOG_PREFIX, describe(e));
        } finally {
            scanning.set(false);
        }
    }

    /**
     * 执行一轮做种清理扫描。
     *
     * <p>该方法不做异常兜底（由 {@link #scanQuietly()} 负责），便于单测直接断言调度行为。</p>
     *
     * @param now 当前时间，允许为 {@code null}（取系统当前时间）
     * @return 本轮成功清理（进入已清理终态）的记录数
     */
    public int scan(Date now) {
        Date current = now == null ? new Date() : now;
        if (!isAria2Reachable()) {
            return 0;
        }
        int cleaned = 0;
        Pageable limit = PageRequest.of(0, MAX_BATCH_SIZE);

        // 1) 上次清理中途崩溃的「清理中」记录：超时后重新执行幂等清理
        List<DownloadSeedTask> stuckTasks = seedTaskRepo.findByStatusAndUpdateAtBefore(
                SeedTaskStatus.CLEANING, new Date(current.getTime() - CLEANING_RETRY_MILLIS), limit).getContent();
        if (!stuckTasks.isEmpty()) {
            log.info("{}发现 {} 条超时未完成的做种清理记录，重新执行幂等清理", LOG_PREFIX, stuckTasks.size());
            cleaned += cleanupBatch(stuckTasks, current);
        }

        // 2) 到期记录：先逐条抢占（0→1），抢不到说明已被其它实例处理
        List<DownloadSeedTask> expiredTasks = seedTaskRepo.findExpired(
                SeedTaskStatus.SEEDING, current, limit).getContent();
        if (!expiredTasks.isEmpty()) {
            log.info("{}发现 {} 条到期的做种记录，开始抢占清理", LOG_PREFIX, expiredTasks.size());
            cleaned += claimAndCleanup(expiredTasks, current);
        }

        if (cleaned > 0) {
            log.info("{}本轮做种清理完成: 已清理 {} 条", LOG_PREFIX, cleaned);
        }
        return cleaned;
    }

    // ==================== 内部实现 ====================

    /**
     * 探测 aria2 是否可达。
     *
     * <p>aria2 不可达时只记 WARN 并让本轮整体跳过（不置终态），避免在 aria2 短暂不可用期间把仍在做种的
     * 目录删掉、导致 aria2 侧任务指向不存在的文件。</p>
     *
     * @return 可达返回 {@code true}
     */
    private boolean isAria2Reachable() {
        try {
            String version = aria2Client.getVersion();
            log.debug("{}aria2 可达: version={}", LOG_PREFIX, version);
            return true;
        } catch (RuntimeException e) {
            log.warn("{}aria2 不可达，跳过本轮做种清理（不置终态，下轮重试）: cause={}", LOG_PREFIX, describe(e));
            return false;
        }
    }

    /**
     * 逐条抢占到期记录，并把抢到的记录交给清理逻辑。
     *
     * @param expiredTasks 已到期的做种记录（状态为做种中）
     * @param now          当前时间
     * @return 成功清理的记录数
     */
    private int claimAndCleanup(List<DownloadSeedTask> expiredTasks, Date now) {
        List<DownloadSeedTask> claimedTasks = new ArrayList<>(expiredTasks.size());
        for (DownloadSeedTask task : expiredTasks) {
            if (task == null || task.getId() == null) {
                continue;
            }
            try {
                int affected = seedTaskRepo.claimExpired(task.getId(), SeedTaskStatus.SEEDING,
                        SeedTaskStatus.CLEANING, now);
                if (affected <= 0) {
                    log.debug("{}做种记录已被其它实例抢占，跳过: id={}, downloadId={}",
                            LOG_PREFIX, task.getId(), task.getDownloadId());
                    continue;
                }
                claimedTasks.add(task);
            } catch (RuntimeException e) {
                log.warn("{}抢占做种记录失败，跳过该记录（下轮重试）: id={}, cause={}",
                        LOG_PREFIX, task.getId(), describe(e));
            }
        }
        return cleanupBatch(claimedTasks, now);
    }

    /**
     * 批量清理一组已被抢占（或需要重试）的做种记录。
     *
     * <p>单条清理失败只影响该条记录：记录会被置为异常终态（{@code status=3}）并写入原因，不中断本轮其它
     * 记录的清理。成功记录按固定大小分批回写终态（项目规范：批量操作避免逐条写库）。</p>
     *
     * @param tasks 待清理的做种记录
     * @param now   当前时间
     * @return 成功清理的记录数
     */
    private int cleanupBatch(List<DownloadSeedTask> tasks, Date now) {
        if (tasks.isEmpty()) {
            return 0;
        }
        int cleaned = 0;
        List<Long> cleanedIds = new ArrayList<>(tasks.size());
        for (DownloadSeedTask task : tasks) {
            try {
                String warning = cleanupOne(task);
                if (warning == null) {
                    cleanedIds.add(task.getId());
                } else {
                    markCleanedWithWarning(task, warning, now);
                }
                cleaned++;
            } catch (RuntimeException e) {
                log.warn("{}做种记录清理失败: id={}, downloadId={}, cause={}",
                        LOG_PREFIX, task.getId(), task.getDownloadId(), describe(e));
                markError(task, e, now);
            }
        }
        if (!cleanedIds.isEmpty()) {
            // 批量回写终态：使用拥有相同结果信息的 id 分批更新，避免逐条 UPDATE
            for (List<Long> batch : SeedTaskBatchUtils.partition(cleanedIds, SeedTaskBatchUtils.DEFAULT_BATCH_SIZE)) {
                try {
                    seedTaskRepo.updateFinishByIds(batch, SeedTaskStatus.CLEANED, CLEANED_MESSAGE, now);
                } catch (RuntimeException e) {
                    log.warn("{}批量回写做种清理终态失败（记录保留「清理中」，下轮重试）: size={}, cause={}",
                            LOG_PREFIX, batch.size(), describe(e));
                }
            }
        }
        return cleaned;
    }

    /**
     * 清理单条做种记录：停止 aria2 做种（尽力）→ 删除本地工作目录。
     *
     * <p>aria2 侧的失败不阻止本地目录删除，原因以返回值形式交给调用方写入 {@code message}；
     * 本地目录删除后仍然存在（例如文件被占用）时抛异常，由调用方置为异常终态。</p>
     *
     * @param task 做种记录
     * @return aria2 清理告警信息；全部正常时返回 {@code null}
     * @throws IllegalStateException 本地目录删除失败（删除后仍存在）或路径非法时抛出
     */
    private String cleanupOne(DownloadSeedTask task) {
        String warning = removeFromAria2(task);
        String localDir = task.getLocalDir();
        if (SfcStringUtils.hasText(localDir)) {
            Path dir = parseLocalDir(localDir);
            DownloadFileUtils.deleteQuietly(dir);
            if (Files.exists(dir)) {
                throw new IllegalStateException("本地目录删除后仍存在: " + dir);
            }
        }
        log.info("{}做种记录已清理: id={}, downloadId={}, gid={}, localDir={}",
                LOG_PREFIX, task.getId(), task.getDownloadId(), task.getContentGid(), localDir);
        return warning;
    }

    /**
     * 从 aria2 移除做种任务：仍 active/waiting/paused 时先 {@code forceRemove}，再清理结果记录。
     *
     * <p>内容 gid 为空时直接跳过（例如磁力任务未能取得内容 gid）。所有 RPC 失败都被转换为告警文本返回，
     * 不向上抛出：aria2 侧的异常不应阻塞本地目录清理。</p>
     *
     * @param task 做种记录
     * @return 告警信息；aria2 侧全部正常时返回 {@code null}
     */
    private String removeFromAria2(DownloadSeedTask task) {
        String gid = task.getContentGid();
        if (!SfcStringUtils.hasText(gid)) {
            return null;
        }
        String trimmedGid = gid.trim();
        try {
            Map<String, Object> status = aria2Client.tellStatus(trimmedGid, List.of("status"));
            Object rawStatus = status == null ? null : status.get("status");
            String aria2Status = rawStatus == null ? "" : String.valueOf(rawStatus);
            if ("active".equals(aria2Status) || "waiting".equals(aria2Status) || "paused".equals(aria2Status)) {
                aria2Client.forceRemove(trimmedGid);
            }
            aria2Client.removeDownloadResult(trimmedGid);
            return null;
        } catch (RuntimeException e) {
            // gid 已不存在（结果记录被清理）或 aria2 中途不可达：继续删除本地目录，把原因写入 message
            return "aria2 清理异常: " + describe(e);
        }
    }

    /**
     * 回写「已清理但 aria2 侧有告警」的终态（逐条写入，因为 message 各不相同）。
     *
     * @param task    做种记录
     * @param warning aria2 清理告警信息
     * @param now     当前时间
     */
    private void markCleanedWithWarning(DownloadSeedTask task, String warning, Date now) {
        String message = truncate(CLEANED_MESSAGE + "；" + warning);
        try {
            seedTaskRepo.updateFinish(task.getId(), SeedTaskStatus.CLEANED, message, now);
        } catch (RuntimeException e) {
            // 回写失败时保持「清理中」，10 分钟后由超时重试机制补写，清理动作本身是幂等的
            log.warn("{}回写做种清理终态失败（记录保留「清理中」，下轮重试）: id={}, cause={}",
                    LOG_PREFIX, task.getId(), describe(e));
        }
    }

    /**
     * 回写异常终态（逐条写入，因为 message 各不相同）。
     *
     * @param task  做种记录
     * @param cause 清理失败的异常
     * @param now   当前时间
     */
    private void markError(DownloadSeedTask task, Throwable cause, Date now) {
        String message = truncate("做种清理失败: " + describe(cause));
        try {
            seedTaskRepo.updateFinish(task.getId(), SeedTaskStatus.ERROR, message, now);
        } catch (RuntimeException e) {
            // 回写失败时保持「清理中」，由超时重试机制在下一轮重新执行清理
            log.warn("{}回写做种记录异常终态失败（记录保留「清理中」，下轮重试）: id={}, cause={}",
                    LOG_PREFIX, task.getId(), describe(e));
        }
    }

    /**
     * 解析本服务侧目录字符串。
     *
     * @param localDir 目录字符串
     * @return 目录路径
     * @throws IllegalStateException 路径语法非法时抛出（该记录会被置为异常终态）
     */
    private static Path parseLocalDir(String localDir) {
        try {
            return Path.of(localDir);
        } catch (InvalidPathException e) {
            throw new IllegalStateException("本地目录路径非法: " + localDir, e);
        }
    }

    /**
     * 截断过长的信息文本，避免超过 {@code message} 列长度上限。
     *
     * @param message 原始文本，允许为 {@code null}
     * @return 截断后的文本
     */
    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        int end = MAX_MESSAGE_LENGTH;
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end) + "...";
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
}
