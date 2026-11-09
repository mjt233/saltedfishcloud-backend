package com.sfc.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 异步任务检查点。
 *
 * <p>保存任务执行者（Worker SDK）上报的任务自定义现场快照（JSON 文本，语义由任务实现自行定义），
 * 用于任务被挂起、执行者停机或失联后由其他执行者接管时恢复现场。</p>
 *
 * <p>与 {@link AsyncTaskRecord} 通过 {@link #taskId} 一一对应，但<b>不继承</b>
 * {@code SfcAuditModel}：主键直接使用任务id，不生成雪花主键，也不记录用户与审计字段。
 * 任务进入终态（完成/失败/取消）后由调度中心删除检查点。</p>
 */
@Entity
@Table(name = "async_task_checkpoint")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskCheckpoint {

    /**
     * 任务id，与 {@link AsyncTaskRecord#getId()} 一一对应，非自增主键
     */
    @Id
    @Column(name = "task_id")
    private Long taskId;

    /**
     * 任务类型，取值与 {@link AsyncTaskRecord#getTaskType()} 一致，主要用于运维排查
     */
    @Column(name = "task_type", length = 64)
    private String taskType;

    /**
     * 任务检查点内容（JSON 文本），语义完全由任务实现自定义，调度中心只做持久化
     */
    @Lob
    private String checkpoint;

    /**
     * 检查点最后更新时间，用于清理长期未更新的残留检查点
     */
    @Column(name = "update_at")
    private Date updateAt;
}
