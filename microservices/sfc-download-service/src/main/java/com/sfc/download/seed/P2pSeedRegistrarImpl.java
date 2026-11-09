package com.sfc.download.seed;

import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.config.SfcDownloadConfigService;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.engine.p2p.P2pSeedRegistrar;
import com.sfc.download.engine.p2p.P2pSeedReuseTarget;
import com.sfc.download.engine.p2p.P2pTaskState;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.model.po.DownloadSeedTask;
import com.sfc.download.repo.DownloadSeedTaskRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 做种保留 SPI 的默认实现：下载完成时登记做种记录、提交前查询可复用做种文件、清理时提供目录保护查询。
 *
 * <p><b>登记（{@link #register}）：</b>读取 core 统一配置中的做种保留时长
 * （{@code download.aria2.seed-time-minutes}）：</p>
 * <ul>
 *   <li>{@code <= 0}：不做种，直接返回 {@code false}，调用方走「完成即清理」收口；</li>
 *   <li>{@code > 0}：写入 {@code download_seed_task} 记录（{@code download_id} 唯一），并把
 *       {@code seeding / seedTaskId / seedEndAt} <b>就地回写</b>到传入的检查点 {@link P2pTaskState}；
 *       检查点由协调器在调用返回后统一下发保存，本类不移交检查点保存职责；</li>
 *   <li>重复登记（例如任务在「登记完成、检查点尚未保存」之间崩溃后重新执行）按「已存在」处理：
 *       复用原记录与到期时间，<b>不延长</b>做种时长。</li>
 * </ul>
 *
 * <p><b>复用查询（{@link #findReusable}）：</b>磁力任务按 URL 中的 infohash、种子任务按
 * {@code torrentBase64} 的内容 SHA-256 匹配「做种中 + 未到期 + 本地目录存在」的记录。命中后调用方不再提交
 * aria2，直接读取现有文件上传，避免 aria2 报 {@code InfoHash is already registered}。查询只读、无副作用；
 * 数据库异常向上抛出，由调用方降级为「正常下载」。</p>
 *
 * <p><b>目录保护（{@link #isProtectedDirectory}）：</b>判断目录是否等于某条「做种中」记录的本地目录。
 * 查询失败<b>必须抛出异常</b>：调用方（陈旧目录清理 / aria2 孤儿任务对账）会按 fail-safe 跳过本轮清理，
 * 吞异常返回 {@code false} 可能导致误删做种中的文件。</p>
 *
 * @see SeedTaskKeeper
 * @see com.sfc.download.engine.p2p.P2pSeedRegistrar
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class P2pSeedRegistrarImpl implements P2pSeedRegistrar {

    /**
     * 日志前缀，与下载服务其它组件保持一致。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 做种到期时间的日志展示格式。
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 做种记录仓储（登记、复用与目录保护查询）。
     */
    private final DownloadSeedTaskRepo seedTaskRepo;

    /**
     * 统一配置访问入口（读取做种保留时长）。
     */
    private final SfcDownloadConfigService configService;

    /**
     * {@inheritDoc}
     *
     * <p>做种保留时长从 core 统一配置读取并取一次快照（{@code resolveSeedTimeMinutes()}）；登记成功时把
     * {@code seeding=true}、{@code seedTaskId}、{@code seedEndAt}（毫秒）就地写回 {@code state}。</p>
     *
     * <p><b>失败语义：</b>配置为 0、缺少下载任务 id、无法确定本地目录、或已存在记录的做种状态不是
     * 「做种中」时返回 {@code false}（调用方按「完成即清理」处理）；数据库其它异常原样向上抛出，
     * 由协调器按任务失败处理，避免「明明该做种却静默删掉文件」。</p>
     *
     * @param context   任务执行上下文，不能为 {@code null}
     * @param state     当前检查点，登记成功时会被就地回写
     * @param artifacts 本次下载产物列表（仅用于日志，允许为空）
     * @return 需要保留本地目录做种时返回 {@code true}
     */
    @Override
    public boolean register(DownloadContext context, P2pTaskState state, List<DownloadArtifact> artifacts) {
        if (context == null || state == null) {
            return false;
        }
        DownloadTaskParams params = context.getParams();
        Long downloadId = params.downloadId == null ? state.getDownloadId() : params.downloadId;
        int seedTimeMinutes = configService.getAria2().resolveSeedTimeMinutes();
        if (seedTimeMinutes <= 0) {
            log.info("{}做种保留时长为 0，不做种保留: downloadId={}", LOG_PREFIX, downloadId);
            context.log("做种保留时长为 0，下载完成后立即清理本地文件");
            return false;
        }
        if (downloadId == null) {
            log.warn("{}下载任务缺少 downloadId，无法登记做种保留", LOG_PREFIX);
            return false;
        }
        String localDir = firstNonBlank(state.getLocalDir(), safeTempDir(context));
        if (!SfcStringUtils.hasText(localDir)) {
            log.warn("{}无法确定本地任务目录，跳过做种登记（避免无法到期清理）: downloadId={}", LOG_PREFIX, downloadId);
            return false;
        }

        DownloadSeedTask existing = seedTaskRepo.findByDownloadId(downloadId).orElse(null);
        DownloadSeedTask record;
        if (existing != null) {
            record = existing;
            log.info("{}做种记录已存在，复用原记录与到期时间: downloadId={}, seedTaskId={}, status={}",
                    LOG_PREFIX, downloadId, record.getId(), record.getStatus());
        } else {
            record = saveNewRecord(context, params, state, downloadId, localDir, seedTimeMinutes);
            if (record == null) {
                return false;
            }
        }
        if (!SeedTaskStatus.SEEDING.equals(record.getStatus())) {
            log.warn("{}做种记录状态不是「做种中」（可能正在清理），本次不保留本地目录: "
                            + "downloadId={}, seedTaskId={}, status={}",
                    LOG_PREFIX, downloadId, record.getId(), record.getStatus());
            return false;
        }
        state.setSeeding(true);
        state.setSeedTaskId(record.getId());
        state.setSeedEndAt(record.getSeedEndAt() == null ? null : record.getSeedEndAt().getTime());
        context.log("已登记做种保留: 保留至 " + formatDate(record.getSeedEndAt()) + "，本地文件保留做种");
        log.info("{}做种保留已登记: downloadId={}, seedTaskId={}, infohash={}, torrentSha256={}, "
                        + "contentGid={}, localDir={}, seedEndAt={}, 产物数={}",
                LOG_PREFIX, downloadId, record.getId(), record.getInfohash(), record.getTorrentSha256(),
                record.getContentGid(), record.getLocalDir(), formatDate(record.getSeedEndAt()),
                artifacts == null ? 0 : artifacts.size());
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>磁力任务按 infohash、种子任务按种子内容 SHA-256 匹配「做种中 + 未到期」的记录，并逐条校验本地目录
     * 是否仍存在（目录被外部删除/清理的脏记录不参与复用）。查询本身无副作用。</p>
     *
     * @param params 下载任务参数，允许为 {@code null}
     * @return 可复用目标；未命中时返回 {@link Optional#empty()}
     */
    @Override
    public Optional<P2pSeedReuseTarget> findReusable(DownloadTaskParams params) {
        if (params == null || params.type == null) {
            return Optional.empty();
        }
        Date now = new Date();
        if (params.type == DownloadType.MAGNET) {
            String infohash = SeedHashUtils.parseMagnetInfoHash(params.url);
            if (!SfcStringUtils.hasText(infohash)) {
                log.debug("{}磁力链 infohash 解析失败，无法复用做种文件: url={}", LOG_PREFIX, params.url);
                return Optional.empty();
            }
            return firstReusable(seedTaskRepo.findActiveByInfohash(infohash, SeedTaskStatus.SEEDING, now),
                    "infohash=" + infohash);
        }
        if (params.type == DownloadType.TORRENT) {
            String torrentSha256 = SeedHashUtils.computeTorrentSha256(params.torrentBase64);
            if (!SfcStringUtils.hasText(torrentSha256)) {
                log.debug("{}种子内容指纹不可用（未携带 torrentBase64），无法复用做种文件: url={}",
                        LOG_PREFIX, params.url);
                return Optional.empty();
            }
            return firstReusable(seedTaskRepo.findByTorrentSha256(torrentSha256, SeedTaskStatus.SEEDING, now),
                    "torrentSha256=" + torrentSha256);
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>只判断「做种中」状态的记录（清理中的文件即将被删除，不再保护）；查询异常不捕获，向上抛出由调用方
     * fail-safe 跳过本轮清理。</p>
     *
     * <p>先按「绝对路径 + 规范化」做精确匹配（走 {@code status} 索引，清理循环中命中即返回）；未命中时再
     * 拉取全部生效中的做种目录，按规范化路径逐一比对，兼容登记录入的目录文本与清理侧传入路径写法不同
     * （冗余分隔符、相对路径、大小写差异等）。</p>
     *
     * @param dir 待判断的本服务侧目录，允许为 {@code null}
     * @return 目录处于做种保护中返回 {@code true}
     */
    @Override
    public boolean isProtectedDirectory(Path dir) {
        if (dir == null) {
            return false;
        }
        Path normalized = dir.toAbsolutePath().normalize();
        if (seedTaskRepo.existsByStatusAndLocalDir(SeedTaskStatus.SEEDING, normalized.toString())) {
            return true;
        }
        for (String activeLocalDir : seedTaskRepo.findAllActiveLocalDirs(SeedTaskStatus.SEEDING)) {
            if (!SfcStringUtils.hasText(activeLocalDir)) {
                continue;
            }
            try {
                if (Path.of(activeLocalDir.trim()).toAbsolutePath().normalize().equals(normalized)) {
                    return true;
                }
            } catch (InvalidPathException e) {
                // 做种记录里的历史脏数据：跳过该条，不影响其它目录的保护判断
                log.debug("{}做种记录中的本服务目录不是合法路径，跳过保护比对: localDir={}", LOG_PREFIX, activeLocalDir);
            }
        }
        return false;
    }

    // ==================== 内部实现 ====================

    /**
     * 新建并保存一条做种记录。
     *
     * <p>{@code download_id} 唯一约束用于兜底并发重复登记：插入冲突时回查已存在记录并按「已存在」处理
     * （返回 {@code null} 表示回查不到，调用方按不做种处理）。</p>
     *
     * @param context         任务执行上下文
     * @param params          下载任务参数
     * @param state           当前检查点
     * @param downloadId      下载任务 id
     * @param localDir        本服务侧任务目录
     * @param seedTimeMinutes 生效的做种保留时长（分钟，已大于 0）
     * @return 已保存（或并发已存在）的做种记录；无法登记时返回 {@code null}
     */
    private DownloadSeedTask saveNewRecord(DownloadContext context, DownloadTaskParams params, P2pTaskState state,
                                           Long downloadId, String localDir, int seedTimeMinutes) {
        Date seedStartAt = new Date();
        Date seedEndAt = new Date(seedStartAt.getTime() + seedTimeMinutes * 60_000L);
        DownloadSeedTask record = new DownloadSeedTask();
        record.setUid(params.uid);
        record.setDownloadId(downloadId);
        record.setContentGid(firstNonBlank(firstOf(state.getContentGids()), firstOf(state.getGids())));
        record.setInfohash(resolveInfohash(params, state));
        record.setTorrentSha256(SeedHashUtils.computeTorrentSha256(params.torrentBase64));
        record.setAria2Dir(state.getAria2Dir());
        record.setLocalDir(localDir);
        record.setSeedStartAt(seedStartAt);
        record.setSeedEndAt(seedEndAt);
        record.setStatus(SeedTaskStatus.SEEDING);
        try {
            return seedTaskRepo.save(record);
        } catch (DataIntegrityViolationException e) {
            // download_id 唯一约束：并发流程（或崩溃重试）已登记过同一下载任务的做种记录
            DownloadSeedTask concurrent = seedTaskRepo.findByDownloadId(downloadId).orElse(null);
            if (concurrent == null) {
                log.warn("{}做种记录插入冲突且回查不到已存在记录，跳过做种留存: downloadId={}, cause={}",
                        LOG_PREFIX, downloadId, e.getMessage());
                context.log("做种记录登记失败，下载完成后将清理本地文件");
            } else {
                log.info("{}做种记录已由并发流程登记，按已存在处理: downloadId={}, seedTaskId={}",
                        LOG_PREFIX, downloadId, concurrent.getId());
            }
            return concurrent;
        }
    }

    /**
     * 从候选做种记录中挑选第一个「本地目录仍存在」的复用目标。
     *
     * @param candidates        候选做种记录（已按状态与到期时间过滤）
     * @param matchDescription  匹配条件描述（仅用于日志）
     * @return 可复用目标；没有可用记录时返回 {@link Optional#empty()}
     */
    private Optional<P2pSeedReuseTarget> firstReusable(List<DownloadSeedTask> candidates,
                                                       String matchDescription) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        for (DownloadSeedTask candidate : candidates) {
            P2pSeedReuseTarget target = toReuseTarget(candidate);
            if (target != null) {
                log.info("{}命中可复用的做种保留记录（{}），无需重新下载: seedTaskId={}, downloadId={}, "
                                + "localDir={}, seedEndAt={}",
                        LOG_PREFIX, matchDescription, target.seedTaskId(), target.downloadId(),
                        target.localDir(), formatDate(target.seedEndAt()));
                return Optional.of(target);
            }
        }
        log.warn("{}命中做种记录但本地目录不可用，忽略复用: {}", LOG_PREFIX, matchDescription);
        return Optional.empty();
    }

    /**
     * 把做种记录转换为复用目标，并校验本地目录仍然存在。
     *
     * @param seed 做种记录
     * @return 复用目标；记录不完整或目录不存在时返回 {@code null}
     */
    private P2pSeedReuseTarget toReuseTarget(DownloadSeedTask seed) {
        if (seed == null || seed.getDownloadId() == null || seed.getSeedEndAt() == null
                || !SfcStringUtils.hasText(seed.getLocalDir())) {
            return null;
        }
        Path localDir;
        try {
            localDir = Path.of(seed.getLocalDir().trim());
        } catch (InvalidPathException e) {
            log.warn("{}做种记录的本服务目录不是合法路径，跳过复用: seedTaskId={}, localDir={}",
                    LOG_PREFIX, seed.getId(), seed.getLocalDir());
            return null;
        }
        if (!Files.isDirectory(localDir)) {
            log.warn("{}做种记录的本服务目录已不存在，跳过复用: seedTaskId={}, localDir={}",
                    LOG_PREFIX, seed.getId(), seed.getLocalDir());
            return null;
        }
        return new P2pSeedReuseTarget(seed.getId(), seed.getDownloadId(), seed.getContentGid(),
                seed.getAria2Dir(), localDir.toAbsolutePath().normalize().toString(), seed.getSeedEndAt());
    }

    /**
     * 解析登记用的 infohash：优先取检查点（磁力链已解析 / 种子任务取自 aria2 状态），回退到从磁力链解析。
     *
     * @param params 下载任务参数
     * @param state  当前检查点
     * @return 小写 hex infohash；无法取得时返回 {@code null}
     */
    private static String resolveInfohash(DownloadTaskParams params, P2pTaskState state) {
        if (SfcStringUtils.hasText(state.getInfohash())) {
            String normalized = SeedHashUtils.normalizeInfoHash(state.getInfohash());
            if (normalized != null) {
                return normalized;
            }
        }
        if (params.type == DownloadType.MAGNET) {
            return SeedHashUtils.parseMagnetInfoHash(params.url);
        }
        return null;
    }

    /**
     * 安全获取任务临时目录文本（用于检查点缺少 {@code localDir} 时兜底登记）。
     *
     * @param context 任务执行上下文
     * @return 目录文本；目录不可用（创建失败）时返回 {@code null}
     */
    private static String safeTempDir(DownloadContext context) {
        try {
            Path tempDir = context.getTempDir();
            return tempDir == null ? null : tempDir.toAbsolutePath().normalize().toString();
        } catch (RuntimeException e) {
            log.warn("{}获取任务临时目录失败，无法登记做种保留: cause={}", LOG_PREFIX, describe(e));
            return null;
        }
    }

    /**
     * 取 gid 列表中的第一个非空白值。
     *
     * @param gids gid 列表，允许为 {@code null}
     * @return 第一个非空白 gid；没有时返回 {@code null}
     */
    private static String firstOf(List<String> gids) {
        if (gids == null) {
            return null;
        }
        for (String gid : gids) {
            if (SfcStringUtils.hasText(gid)) {
                return gid.trim();
            }
        }
        return null;
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param first  首选值，允许为 {@code null}
     * @param second 备选值，允许为 {@code null}
     * @return 第一个非空白值；都为空时返回 {@code null}
     */
    private static String firstNonBlank(String first, String second) {
        if (SfcStringUtils.hasText(first)) {
            return first.trim();
        }
        return SfcStringUtils.hasText(second) ? second.trim() : null;
    }

    /**
     * 把时间格式化为可读文本（用于日志与任务日志）。
     *
     * @param date 时间，允许为 {@code null}
     * @return 格式化文本；入参为空时返回「未知」
     */
    private static String formatDate(Date date) {
        return date == null ? "未知" : DATE_FORMATTER.format(date.toInstant().atZone(ZoneId.systemDefault()));
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
