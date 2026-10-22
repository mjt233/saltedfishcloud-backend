package com.sfc.cloud.task.worker.protocol;

import java.util.List;

/**
 * 任务执行结束上报参数，镜像 core 的 {@code com.sfc.task.protocol.TaskFinishReportParam}。
 *
 * @param taskId       任务id
 * @param workerId     执行者标识
 * @param status       任务最终状态，只允许 FINISH(2)、FAILED(3)、CANCEL(4) 三者之一
 * @param message      结束说明，失败时建议填写错误信息
 * @param tailLogLines 任务结束前尚未上报的尾部日志
 */
public record TaskFinishReportParam(
        Long taskId,
        String workerId,
        Integer status,
        String message,
        List<String> tailLogLines
) {
}
