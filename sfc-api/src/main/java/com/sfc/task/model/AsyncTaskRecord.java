package com.sfc.task.model;

import com.sfc.task.AsyncTaskConstants;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.sfc.cloud.data.model.SfcAuditModel;
import lombok.*;

import jakarta.persistence.*;
import java.util.Date;

/**
 * 异步任务记录
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_async_task_record_uid", columnList = "uid"),
        @Index(name = "idx_async_task_record_claim", columnList = "status,taskType,createAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskRecord extends SfcAuditModel {

    /**
     * 任务类型，用于区分任务的所属类型，如按下载任务、视频转码任务等进行划分，具体由各任务创建者决定。<br>
     * 系统内置的任务类型可参考 {@link AsyncTaskType}
     */
    private String taskType;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务的创建参数
     */
    @Lob
    private String params;

    /**
     * 执行日期
     */
    private Date executeDate;

    /**
     * 完成日期
     */
    private Date finishDate;

    /**
     * 失败日期
     */
    private Date failedDate;

    /**
     * 负责该任务的执行节点信息
     */
    private String executor;

    /**
     * 运行状态<br>
     * <ul>
     *     <li>等待中 - 0</li>
     *     <li>运行中 - 1</li>
     *     <li>执行完成 - 2</li>
     *     <li>任务失败 - 3</li>
     *     <li>已取消 - 4</li>
     *     <li>任务离线 - 5</li>
     * </ul>
     *
     * @see AsyncTaskConstants.Status
     */
    private Integer status;

    /**
     * CPU开销指数，取值范围: >= 0，该项参数会影响任务的并行执行数量，后续可能还会作为集群异步任务的分配决策依据<br>
     * 如100表示该任务可能会耗尽1个CPU核心的计算能力
     */
    private Integer cpuOverhead;

    /**
     * 是否为临时任务<br>
     * 临时任务无需等待排队认领任务，可立即执行，且不具备生命周期管理的自动重试功能
     */
    private Boolean isTemp;

    /**
     * 认领该任务的执行者标识。<br>
     * 本地执行节点为 {@code node-{节点id}}，微服务执行者为该 worker 注册的实例id。<br>
     * 为空表示任务尚未被任何执行者认领。
     */
    private String claimedWorker;

    /**
     * 任务被认领的时间
     */
    private Date claimedAt;

    /**
     * 执行者最后一次上报（开始/进度/日志/完成）的时间，用于故障转移时判断执行者是否仍然存活
     */
    private Date lastReportAt;

    /**
     * 故障转移重试次数。任务执行者失联被回收重发时会累加，达到上限后任务置为失败，避免无限重试
     */
    private Integer retryCount;

    @Transient
    private Boolean isFailed;

}
