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
     * 临时任务不参与故障转移重试：执行者失联或主动放弃执行权时直接置为失败，不会被重新排队。
     */
    private Boolean isTemp;

    /**
     * 任务优先级，数值越大越优先被认领。<br>
     * 默认为 {@link AsyncTaskConstants.Priority#NORMAL}；提交时 {@code immediate=true} 的任务为
     * {@link AsyncTaskConstants.Priority#URGENT}，执行者满载时也会优先认领执行。
     */
    private Integer priority;

    /**
     * 认领该任务的执行者所属服务名（服务发现中的 serviceId）<br>
     * 例如 core 节点为 {@code sfc-core}，微服务为 {@code sfc-demo-service}。与
     * {@link #claimedWorker} 一起用于在服务发现中判定执行者是否存活，以及定位中断回调地址。
     */
    private String claimedService;

    /**
     * 认领该任务的执行者标识（实例在服务注册中心里的地址 {@code host:port}）<br>
     * core 节点与微服务实例都使用各自在服务注册中心注册的 host:port。为空表示任务尚未被任何执行者认领。
     */
    private String claimedWorker;

    /**
     * 认领该任务的执行者角色，取值见 {@link AsyncTaskConstants.WorkerRole}，用于展示与排障。<br>
     * 中断指令统一按服务发现定位执行者实例地址后下发，与角色无关。
     */
    private String claimedRole;

    /**
     * 任务被认领的时间
     */
    private Date claimedAt;

    /**
     * 执行者最后一次上报（开始/进度/日志/完成）的时间，用于诊断
     */
    private Date lastReportAt;

    /**
     * 故障转移重试次数。任务执行者失联被回收重发时会累加，达到上限后任务置为失败，避免无限重试
     */
    private Integer retryCount;

    @Transient
    private Boolean isFailed;

}
