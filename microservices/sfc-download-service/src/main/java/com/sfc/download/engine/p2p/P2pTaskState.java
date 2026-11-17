package com.sfc.download.engine.p2p;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.common.utils.SfcStringUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * P2P（磁力链 / 种子）下载任务的检查点模型。
 *
 * <p>本模型随异步任务检查点（core 的 {@code async_task_checkpoint} 表）持久化，用于下载服务停机、
 * 崩溃或跨实例接管时恢复 aria2 下载现场。JSON 形态与设计契约 {@code docs/plan/aria2-resume-seed-design.md}
 * §3.1 一致，序列化统一经 {@link SfcMapperHolder}（与项目其它 JSON 落库场景保持同一套 ObjectMapper
 * 配置）。</p>
 *
 * <p><b>阶段状态机：</b></p>
 * <pre>
 * PREPARED ──提交 aria2──► SUBMITTED ──首轮状态──► DOWNLOADING
 *    │                                              │
 *    └────────── 失败/取消清理 ◄────────────────────┘
 *                                                   │ 全部 gid complete + 产物采集
 *                                                   ▼
 *                                               DOWNLOADED ──(登记做种)──► [seeding=true]
 *                                                   │
 *                                                   ▼
 *                                               UPLOADING ──上传成功──► UPLOADED ──► FINISH
 * </pre>
 *
 * <p><b>目录约定：</b>{@link #localDir} 与 {@link #aria2Dir} 在首次创建后<b>不再变化</b>；恢复时复用同一
 * 本服务目录与 aria2 目录，配合强制 {@code continue=true} 实现「同目录尽力续传」。</p>
 *
 * <p><b>容错：</b>{@link #fromJson(String)} 在 JSON 非法或版本不认识时只记录 WARN 并返回
 * {@link Optional#empty()}，按「无检查点」处理（重新下载），绝不因检查点损坏卡死任务。</p>
 *
 * <p><b>并发：</b>本模型只在单个任务执行线程内读写（检查点保存/读取均由任务线程触发），因此不要求
 * 线程安全；{@code seed-*} 字段由 {@link P2pSeedRegistrar} 实现回写。</p>
 */
@Slf4j
@Data
public class P2pTaskState {

    /**
     * 当前检查点格式版本。
     *
     * <p>版本不认识时按「无检查点」处理，避免旧格式被误解释。</p>
     */
    public static final int CURRENT_VERSION = 1;

    /**
     * 检查点格式版本，升级不兼容格式时递增。
     */
    private int version = CURRENT_VERSION;

    /**
     * 下载任务主键（{@code download_task.id}），用于校验检查点与当前任务是否匹配。
     */
    private Long downloadId;

    /**
     * 生命周期阶段，取值见 {@link Phase}。
     */
    private Phase phase;

    /**
     * 本服务侧任务目录（共享目录模式下为 {@code <shared-dir>/offline-download/<downloadId>-<uuid>}）。
     *
     * <p>首次创建后不再变化；恢复时直接复用该目录。</p>
     */
    private String localDir;

    /**
     * aria2 侧任务目录（提交时作为强制 {@code dir} 选项），首次创建后不再变化。
     */
    private String aria2Dir;

    /**
     * 已跟踪的全部 aria2 gid。
     *
     * <p>磁力链包含「元数据 gid」与「内容 gid」；用于停机恢复时重连 {@code tellStatus}。</p>
     */
    private List<String> gids = new ArrayList<>();

    /**
     * 真正产出文件的 aria2 gid 列表。
     *
     * <p>磁力链为 {@code followedBy} 指向的内容 gid；种子任务即提交返回的 gid。做种登记与到期清理
     * 使用其中的代表 gid。</p>
     */
    private List<String> contentGids = new ArrayList<>();

    /**
     * torrent infohash（小写 hex，磁力链可解析、种子任务取自 aria2 {@code bittorrent.infoHash}）；
     * 未知时为 {@code null}。
     */
    private String infohash;

    /**
     * 接管次数：每被一次执行者接管（resume）+1；超过配置上限视为异常任务。
     */
    private int resumeCount;

    /**
     * 首次提交 aria2 的时间（毫秒时间戳）。
     */
    private long submittedAt;

    /**
     * 检查点最后更新时间（毫秒时间戳）。
     */
    private long updatedAt;

    /**
     * 是否已登记做种保留（由 {@link P2pSeedRegistrar} 回写）；为 {@code true} 时本地目录必须保留。
     */
    private boolean seeding;

    /**
     * 做种记录主键（{@code download_seed_task.id}），未登记做种时为 {@code null}。
     */
    private Long seedTaskId;

    /**
     * 做种到期时间（毫秒时间戳），未登记做种时为 {@code null}。
     */
    private Long seedEndAt;

    /**
     * 上传开始时间（毫秒时间戳），未进入上传阶段时为 {@code null}。
     *
     * <p>接管实例据此判断「旧实例是否可能仍在上传」：未超过陈旧阈值时先等待并重读检查点。</p>
     */
    private Long uploadStartedAt;

    /**
     * 上传是否已成功（{@link Phase#UPLOADED} 时为 {@code true}）。
     *
     * <p>接管实例看到 {@code true} 时直接补报完成，<b>不重复上传</b>。</p>
     */
    private boolean uploaded;

    /**
     * 上传成功后的网盘保存名（单文件为文件名，目录树为顶层目录名）。
     */
    private String savedName;

    /**
     * 上传成功的字节数合计。
     */
    private long savedBytes;

    /**
     * 生命周期阶段。
     *
     * <p>只允许按状态机单向推进；失败 / 取消 / 挂起由收口矩阵决定清理或保留，不新增阶段值。</p>
     */
    public enum Phase {

        /** 已创建任务目录并落盘检查点，尚未提交 aria2。 */
        PREPARED,

        /** 已提交 aria2，尚未拿到首轮状态。 */
        SUBMITTED,

        /** aria2 正在下载（等待接管恢复时同样停留在此阶段的语义之外，由 {@link #isDownloading()} 判定）。 */
        DOWNLOADING,

        /** aria2 下载完成且产物已采集，尚未进入上传。 */
        DOWNLOADED,

        /** 正在上传网盘。 */
        UPLOADING,

        /** 上传成功，等待上报完成。 */
        UPLOADED;

        /**
         * 是否处于「下载中」阶段（{@link #SUBMITTED} 或 {@link #DOWNLOADING}）。
         *
         * @return 处于下载中阶段返回 {@code true}
         */
        public boolean isDownloading() {
            return this == SUBMITTED || this == DOWNLOADING;
        }
    }

    /**
     * 构造一个「已创建任务目录、尚未提交 aria2」的初始检查点。
     *
     * @param downloadId 下载任务主键
     * @param localDir   本服务侧任务目录
     * @param aria2Dir   aria2 侧任务目录
     * @return 阶段为 {@link Phase#PREPARED} 的检查点
     */
    public static P2pTaskState newPrepared(Long downloadId, Path localDir, String aria2Dir) {
        P2pTaskState state = new P2pTaskState();
        state.downloadId = downloadId;
        state.phase = Phase.PREPARED;
        state.localDir = localDir == null ? null : localDir.toAbsolutePath().normalize().toString();
        state.aria2Dir = aria2Dir;
        long now = System.currentTimeMillis();
        state.submittedAt = now;
        state.updatedAt = now;
        return state;
    }

    /**
     * 序列化为检查点 JSON 文本。
     *
     * @return JSON 文本，永不为 {@code null}
     * @throws SfcJsonException 序列化失败时抛出（正常情况下不会发生）
     */
    public String toJson() {
        try {
            return SfcMapperHolder.toJson(this);
        } catch (IOException | RuntimeException e) {
            throw new SfcJsonException(500, "P2P 检查点序列化失败: " + describe(e));
        }
    }

    /**
     * 从检查点 JSON 文本反序列化。
     *
     * <p>以下情况只记录 WARN 并返回 {@link Optional#empty()}（按「无检查点」处理，重新下载）：
     * 空白文本、JSON 非法、版本不认识、缺少阶段。</p>
     *
     * @param json 检查点 JSON 文本，允许为 {@code null}
     * @return 反序列化结果；不可用时返回空
     */
    public static Optional<P2pTaskState> fromJson(String json) {
        if (!SfcStringUtils.hasText(json)) {
            return Optional.empty();
        }
        P2pTaskState state;
        try {
            state = SfcMapperHolder.parseJson(json, P2pTaskState.class);
        } catch (IOException | RuntimeException e) {
            log.warn("{}P2P 检查点解析失败，按无检查点处理: cause={}",
                    com.sfc.download.constant.DownloadTaskConstants.LOG_PREFIX, describe(e));
            return Optional.empty();
        }
        if (state == null) {
            return Optional.empty();
        }
        if (state.version != CURRENT_VERSION) {
            log.warn("{}P2P 检查点版本不支持，按无检查点处理: version={}, supported={}",
                    com.sfc.download.constant.DownloadTaskConstants.LOG_PREFIX,
                    state.version, CURRENT_VERSION);
            return Optional.empty();
        }
        if (state.phase == null) {
            log.warn("{}P2P 检查点缺少阶段字段，按无检查点处理: downloadId={}",
                    com.sfc.download.constant.DownloadTaskConstants.LOG_PREFIX, state.downloadId);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    /**
     * 获取本服务侧任务目录。
     *
     * @return 任务目录；{@link #localDir} 为空或不是合法路径时返回 {@code null}
     */
    public Path localDirPath() {
        return toPath(localDir);
    }

    /**
     * 获取 aria2 侧任务目录。
     *
     * @return aria2 侧目录；{@link #aria2Dir} 为空或不是合法路径时返回 {@code null}
     */
    public Path aria2DirPath() {
        return toPath(aria2Dir);
    }

    /**
     * 获取做种到期时间。
     *
     * @return 做种到期时间；未登记做种时返回 {@code null}
     */
    public Date seedEndAtDate() {
        return seedEndAt == null ? null : new Date(seedEndAt);
    }

    /**
     * 判断检查点是否指向指定的下载任务。
     *
     * @param otherDownloadId 下载任务主键，允许为 {@code null}
     * @return 两者主键一致返回 {@code true}
     */
    public boolean belongsTo(Long otherDownloadId) {
        return downloadId != null && downloadId.equals(otherDownloadId);
    }

    /**
     * 用给定的 gid 集合替换 {@link #gids}（保持顺序、去重）。
     *
     * @param latestGids 最新的完整 gid 集合，允许为 {@code null}
     */
    public void replaceGids(Collection<String> latestGids) {
        this.gids = new ArrayList<>();
        if (latestGids != null) {
            for (String gid : latestGids) {
                if (SfcStringUtils.hasText(gid) && !this.gids.contains(gid)) {
                    this.gids.add(gid.trim());
                }
            }
        }
    }

    /**
     * 用给定的 gid 集合替换 {@link #contentGids}（保持顺序、去重）。
     *
     * @param latestContentGids 最新的内容 gid 集合，允许为 {@code null}
     */
    public void replaceContentGids(Collection<String> latestContentGids) {
        this.contentGids = new ArrayList<>();
        if (latestContentGids != null) {
            for (String gid : latestContentGids) {
                if (SfcStringUtils.hasText(gid) && !this.contentGids.contains(gid)) {
                    this.contentGids.add(gid.trim());
                }
            }
        }
    }

    /**
     * 刷新检查点更新时间。
     */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 判断当前阶段是否为指定的阶段。
     *
     * @param expected 期望的阶段，允许为 {@code null}
     * @return 阶段一致返回 {@code true}
     */
    public boolean isPhase(Phase expected) {
        return phase == expected;
    }

    /**
     * 把目录文本解析为路径。
     *
     * @param text 目录文本
     * @return 路径；空白或语法非法时返回 {@code null}
     */
    private static Path toPath(String text) {
        if (!SfcStringUtils.hasText(text)) {
            return null;
        }
        try {
            return Path.of(text.trim());
        } catch (InvalidPathException e) {
            return null;
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
        return SfcStringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }
}
