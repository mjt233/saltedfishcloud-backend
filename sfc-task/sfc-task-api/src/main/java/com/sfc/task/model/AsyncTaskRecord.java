package com.sfc.task.model;

import com.sfc.task.AsyncTaskConstants;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.*;
import org.hibernate.annotations.Proxy;

import javax.persistence.*;
import java.util.Date;

/**
 * 异步任务记录
 */
@Entity(name = "async_task_record")
@Table(indexes = {
        @Index(name = "idx_uid", columnList = "uid")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Proxy(lazy = false)
public class AsyncTaskRecord extends AuditModel {

    /**
     * 任务类型，用于区分任务的所属类型，如按下载任务、视频转码任务等进行划分，具体由各任务创建者决定。
     */
    private String taskType;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务的创建参数
     */
    @Column(columnDefinition = "LONGTEXT COMMENT '任务参数'")
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

    @Transient
    private Boolean isFailed;

}
