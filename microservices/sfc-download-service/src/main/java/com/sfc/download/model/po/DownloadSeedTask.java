package com.sfc.download.model.po;

import com.sfc.cloud.data.model.SfcAuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 做种保留记录（本服务独立数据库中的 {@code download_seed_task} 表）。
 *
 * <p>P2P 下载（磁力链 / 种子）完成后，如果「做种保留时长」配置大于 0，就登记一条本记录：保存 aria2 侧与
 * 本服务侧的任务目录、infohash / 种子内容指纹以及做种到期时间。到期后由
 * {@link com.sfc.download.seed.SeedTaskKeeper} 周期扫描，抢占记录 → 停止 aria2 做种 → 删除本地工作目录，
 * 最后把状态置为「已清理」；网盘中的副本不受影响。</p>
 *
 * <p><b>表结构与列名是设计契约的一部分，不得随意改动：</b></p>
 * <ul>
 *   <li>{@code download_id} 唯一：一个下载任务最多一条做种记录；</li>
 *   <li>{@code infohash}：磁力链从链接解析、种子任务取自 aria2 的 {@code bittorrent.infoHash}，用于
 *       同 infohash 复用查询；未知时为 {@code null}；</li>
 *   <li>{@code torrent_sha256}：种子任务可选，种子内容的 SHA-256（同种子复用匹配），磁力任务为
 *       {@code null}；</li>
 *   <li>{@code status + seed_end_at} 复合索引：到期扫描按「做种中且已到期」过滤。</li>
 * </ul>
 *
 * <p><b>列名为什么显式指定：</b>Hibernate 的驼峰转下划线策略无法把 {@code aria2Dir} 正确转换为契约要求的
 * {@code aria2_dir}（数字后跟大写字母时不会插入下划线），因此所有多词字段都显式声明列名，保证生成的表结构与
 * 设计契约逐字一致（与同模块的 {@link DownloadTaskInfo} 保持同一做法）。</p>
 *
 * <p>主键、{@code uid} 与创建/更新时间来自基类 {@link SfcAuditModel}（雪花主键 + JPA 审计）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "download_seed_task", indexes = {
        @Index(name = "idx_download_seed_task_download_id", columnList = "download_id", unique = true),
        @Index(name = "idx_download_seed_task_infohash", columnList = "infohash"),
        @Index(name = "idx_download_seed_task_status_seed_end_at", columnList = "status,seed_end_at")
})
public class DownloadSeedTask extends SfcAuditModel {

    /**
     * 关联的下载任务 id（唯一）。
     *
     * <p>对应 {@code download_task.id}；一个下载任务最多一条做种记录。任务列表展示做种状态时按它批量回查
     * 本表。</p>
     */
    @Column(name = "download_id", nullable = false)
    private Long downloadId;

    /**
     * 真正产出内容的 aria2 gid（用于到期清理时移除 aria2 任务）。
     *
     * <p>磁力链是「元数据 gid → 内容 gid」两阶段，这里保存内容 gid；取不到内容 gid 时为 {@code null}，
     * 到期清理仍会删除本地目录。</p>
     */
    @Column(name = "content_gid", length = 64)
    private String contentGid;

    /**
     * 种子的 infohash（小写 hex，40 字符）。
     *
     * <p>磁力链从链接的 {@code xt=urn:btih:...} 解析（兼容 40 位 hex 与 32 位 base32）；种子任务取自
     * aria2 状态的 {@code bittorrent.infoHash}；都无法取得时为 {@code null}。</p>
     */
    @Column(length = 64)
    private String infohash;

    /**
     * 种子内容的 SHA-256（小写 hex，64 字符，可选）。
     *
     * <p>仅种子任务在任务参数携带 {@code torrentBase64} 时计算；磁力任务没有种子文件内容，为
     * {@code null}。用于「同一种子文件」的复用匹配。</p>
     */
    @Column(name = "torrent_sha256", length = 64)
    private String torrentSha256;

    /**
     * aria2 侧的任务目录（提交 aria2 时的 {@code dir}，可能经过共享目录映射）。
     *
     * <p>仅用于排障展示与「aria2 侧目录与本服务侧目录」的对照，不参与本地删除。</p>
     */
    @Column(name = "aria2_dir", length = 2048)
    private String aria2Dir;

    /**
     * 本服务侧的任务目录（做种到期后要删除的目标）。
     *
     * <p>即 {@code DownloadContext#getTempDir()} 的绝对路径；共享目录部署时位于
     * {@code <sharedDir>/offline-download/<下载任务id>-<UUID>}。</p>
     */
    @Column(name = "local_dir", length = 2048)
    private String localDir;

    /**
     * 做种开始时间（即下载完成、登记做种的时间）。
     */
    @Column(name = "seed_start_at")
    private Date seedStartAt;

    /**
     * 做种到期时间（{@code seed_start_at + seedTimeMinutes}）。
     *
     * <p>到期扫描的条件列；同 infohash 复用时该时间保持不变，不因新的下载任务而延长。</p>
     */
    @Column(name = "seed_end_at")
    private Date seedEndAt;

    /**
     * 做种记录状态，取值见 {@link com.sfc.download.seed.SeedTaskStatus}。
     *
     * <p>0=做种中，1=清理中（已被某个实例抢占），2=已清理，3=异常（清理失败，需人工介入）。</p>
     */
    private Integer status;

    /**
     * 清理结果或异常信息（写入 aria2 移除告警、删除目录失败原因等，最长 1024 字符）。
     */
    @Column(length = 1024)
    private String message;

    /**
     * 进入终态的时间（做种记录被清理或标记异常的时间）。
     */
    @Column(name = "finish_at")
    private Date finishAt;
}
