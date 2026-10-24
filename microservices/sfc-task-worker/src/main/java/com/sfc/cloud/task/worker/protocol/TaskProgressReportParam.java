package com.sfc.cloud.task.worker.protocol;

import java.util.Map;

/**
 * 任务进度上报参数（执行者 → core）。
 *
 * <p>执行者按固定节流间隔（约 1 秒）上报进度，core 将其写入任务进度缓存；前端进度查询无需区分
 * 任务是宿主内嵌执行者还是微服务实例执行。</p>
 *
 * @param taskId         任务id
 * @param workerId       执行者标识
 * @param loaded         已完成量
 * @param total          总量，-1 表示未知
 * @param speed          每秒完成量（由执行者计算）
 * @param lastUpdateTime 进度最后更新时间（Unix 时间戳毫秒）
 * @param extensions     进度扩展信息（可空）。内嵌执行者上报完整进度快照时，快照中除
 *                       {@code loaded} / {@code total} / {@code speed} / {@code lastUpdateTime}
 *                       之外的字段会放在这里；微服务执行者通常为 null
 */
public record TaskProgressReportParam(
        Long taskId,
        String workerId,
        Long loaded,
        Long total,
        Long speed,
        Long lastUpdateTime,
        Map<String, Object> extensions
) {
}
