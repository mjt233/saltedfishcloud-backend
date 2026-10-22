package com.sfc.cloud.task.worker.protocol;

/**
 * 任务进度上报参数，镜像 core 的 {@code com.sfc.task.protocol.TaskProgressReportParam}。
 *
 * @param taskId         任务id
 * @param workerId       执行者标识
 * @param loaded         已完成量
 * @param total          总量，-1 表示未知
 * @param speed          每秒完成量（由执行者计算）
 * @param lastUpdateTime 进度最后更新时间（Unix 时间戳毫秒）
 */
public record TaskProgressReportParam(
        Long taskId,
        String workerId,
        Long loaded,
        Long total,
        Long speed,
        Long lastUpdateTime
) {
}
