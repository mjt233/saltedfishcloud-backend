package com.sfc.task.protocol;

import java.util.List;

/**
 * 任务运行日志上报参数。
 * <p>执行者将运行中产生的日志按批上报，core 负责写入运行日志缓冲、推送到日志消息主题供前端
 * 实时监听，并在任务结束时统一落库。</p>
 *
 * @param taskId   任务id
 * @param workerId 执行者标识
 * @param lines    日志行内容
 */
public record TaskLogReportParam(Long taskId, String workerId, List<String> lines) {
}
