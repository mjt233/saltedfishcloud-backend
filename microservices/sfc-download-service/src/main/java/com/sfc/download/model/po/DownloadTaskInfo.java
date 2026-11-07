package com.sfc.download.model.po;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sfc.cloud.data.model.SfcAuditModel;
import com.sfc.download.model.vo.AsyncTaskRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 离线下载任务（本服务独立的 SQLite 表）。
 *
 * <p>由旧插件 {@code sfc-ext-download} 的 {@code DownloadTaskInfo} 迁移而来：主键由宿主字符串 uuid
 * 改为共享基座的雪花 {@link Long} id（{@link SfcAuditModel}），并去掉对宿主
 * {@code AsyncTaskRecord} 实体的 JPA 关联——异步任务状态改为 {@code taskId} + 列表查询时回源校准。</p>
 *
 * <p><b>字段兼容：</b>前端读取的是 {@code createdAt}（旧插件字段名），而 {@link SfcAuditModel}
 * 提供的审计字段名为 {@code createAt}，因此额外暴露 {@link #getCreatedAt()} 只读别名。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "download_task", indexes = {
        @Index(name = "idx_download_task_uid", columnList = "uid"),
        @Index(name = "idx_download_task_status", columnList = "status")
})
public class DownloadTaskInfo extends SfcAuditModel {

    /**
     * 下载类型编码（取值见 {@link com.sfc.download.engine.DownloadType#getCode()}）。
     *
     * <p>创建任务时由请求体中的 {@code type} 原样写入，服务端不做推导；该列<b>刻意允许为空</b>：
     * SQLite 对已有数据的表执行 {@code ALTER TABLE ... ADD COLUMN ... NOT NULL} 会直接失败，
     * 因此升级前的历史记录读出来是 {@code null}。实体与查询逻辑都不为其补默认值，
     * 以免把「历史未知」伪装成「明确是某个类型」。</p>
     */
    @Column(length = 32)
    private String type;

    /**
     * 下载来源。
     *
     * <p>HTTP / Git / 磁力链 / ed2k 任务直接是该来源的地址；<b>种子任务在「由客户端上传种子内容」的场景下
     * 可以为空</b>，此时种子内容由任务参数的 {@code torrentBase64} 提供。因此本字段不使用
     * {@code @NotEmpty} 约束，来源合法性统一由下载引擎在创建期校验。</p>
     */
    @Lob
    private String url;

    /**
     * 下载时使用的代理名称（来自 core 的代理配置；为空表示直连）。
     */
    private String proxy;

    /**
     * 任务失败时的错误信息。
     */
    @Lob
    private String message;

    /**
     * 已下载字节数。
     */
    private long loaded;

    /**
     * 文件总字节数（未知时为 0 或 -1）。
     */
    private long size;

    /**
     * 任务展示名称（列表接口原样返回，前端用于展示「任务名称」）。
     *
     * <p>取值随下载类型不同：</p>
     * <ul>
     *   <li><b>HTTP / GIT</b>：成功完成后写入保存到网盘的文件名或顶层目录名；HTTP 在响应到达后还会先
     *       写入探测到的文件名，任务运行中即可展示；</li>
     *   <li><b>MAGNET / TORRENT</b>：下载文件名摘要——单个文件直接用文件名；两到三个文件完整拼接
     *       （以 {@code 、} 分隔）；超过三个文件只列前三个并追加 {@code 等...}。运行中拿到文件清单后
     *       即写入，完成时按实际下载的产物校正。多文件任务的该字段<b>仅用于展示</b>，不等于网盘中的
     *       真实顶层目录名（真实目录名仍是种子信息名）；</li>
     *   <li><b>失败 / 取消</b>：保留执行过程中已写入的名称；从未解析出名称时为 {@code null}。</li>
     * </ul>
     */
    @Column(length = 1024)
    private String name;

    /**
     * 保存到网盘的目录路径。
     */
    @Column(name = "save_path", length = 2048)
    private String savePath;

    /**
     * 任务创建者的 uid（资源归属人见继承字段 {@code uid}，两者可能因管理员代操作而不同）。
     */
    @Column(name = "created_by")
    private long createdBy;

    /**
     * 任务结束时间（完成 / 失败 / 取消时写入）。
     */
    @Column(name = "finish_at")
    private Date finishAt;

    /**
     * 关联的异步任务 id（提交成功后回填，用于中断与状态校准）。
     */
    @Column(name = "task_id")
    private Long taskId;

    /**
     * 异步任务状态镜像，取值见 {@link com.sfc.download.constant.DownloadTaskConstants.Status}。
     *
     * <p>该列只用于列表筛选与排障展示，权威状态以 core 的异步任务记录为准；
     * 列表接口会用 core 的实时状态校准它。</p>
     */
    private Integer status;

    /**
     * 下载速度（字节/秒），仅用于接口展示，不持久化。
     */
    @Transient
    private long speed;

    /**
     * 关联异步任务的状态快照，仅用于接口展示，不持久化。
     */
    @Transient
    private AsyncTaskRef asyncTaskRecord;

    /**
     * 创建时间的别名，兼容前端旧字段 {@code createdAt}。
     *
     * <p>旧插件的实体字段名为 {@code createdAt}；迁移到共享基座后审计字段为 {@code createAt}，
     * 因此保留一个只读别名避免前端适配。</p>
     *
     * @return 任务创建时间
     */
    @JsonProperty("createdAt")
    public Date getCreatedAt() {
        return getCreateAt();
    }
}
