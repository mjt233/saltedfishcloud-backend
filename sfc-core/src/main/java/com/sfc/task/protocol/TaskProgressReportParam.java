package com.sfc.task.protocol;

/**
 * 任务进度上报参数。
 * <p>执行者按固定节流间隔（约 1 秒）上报进度，core 将其写入任务进度缓存，
 * 前端进度查询接口无需区分任务是在 core 还是微服务上执行。</p>
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
