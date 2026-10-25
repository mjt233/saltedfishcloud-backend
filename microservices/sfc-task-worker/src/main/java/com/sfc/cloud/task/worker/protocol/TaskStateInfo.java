package com.sfc.cloud.task.worker.protocol;

import java.util.Date;

/**
 * 任务状态与最新进度快照（core → 调用方）。
 *
 * <p>这是<b>入站</b>报文：core 处理 {@link TaskStateQueryParam} 后，以本 record 列表作为
 * {@code POST /api/internal/task/query} 的响应数据返回。</p>
 *
 * <p>{@link #status()} 与任务记录的状态一致，取值 0~5，含义见 core 的异步任务状态常量
 * （{@code com.sfc.task.AsyncTaskConstants.Status}）：0 等待中、1 运行中、2 执行完成、3 任务失败、
 * 4 已取消、5 任务离线。进度字段来自 core 的进度快照缓存：任务没有进度快照（尚未开始、已结束或快照过期）时
 * {@link #loaded()} / {@link #total()} / {@link #speed()} 均为 null。</p>
 *
 * @param taskId     任务id
 * @param status     任务状态，取值 0~5（0 等待中、1 运行中、2 执行完成、3 任务失败、4 已取消、5 任务离线）
 * @param loaded     已完成的量（来自进度快照），无进度快照时为 null
 * @param total      目标总量（来自进度快照），无进度快照时为 null；-1 表示总量未知
 * @param speed      速度（来自进度快照，单位由任务实现自行约定），无进度快照时为 null
 * @param finishDate 结束时间：执行完成的取完成时间，失败/取消的取失败时间；任务尚未结束时为 null
 */
public record TaskStateInfo(
        Long taskId,
        Integer status,
        Long loaded,
        Long total,
        Long speed,
        Date finishDate
) {
}
