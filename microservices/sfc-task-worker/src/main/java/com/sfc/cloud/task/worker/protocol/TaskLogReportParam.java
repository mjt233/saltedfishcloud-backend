package com.sfc.cloud.task.worker.protocol;

import java.util.List;

/**
 * 任务运行日志上报参数，镜像 core 的 {@code com.sfc.task.protocol.TaskLogReportParam}。
 *
 * @param taskId   任务id
 * @param workerId 执行者标识
 * @param lines    日志行内容
 */
public record TaskLogReportParam(Long taskId, String workerId, List<String> lines) {
}
