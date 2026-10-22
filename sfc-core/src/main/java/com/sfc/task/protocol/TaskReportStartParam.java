package com.sfc.task.protocol;

/**
 * 任务开始执行上报参数。
 *
 * @param taskId   任务id
 * @param workerId 执行者标识
 */
public record TaskReportStartParam(Long taskId, String workerId) {
}
