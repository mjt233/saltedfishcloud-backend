package com.sfc.task.protocol;

import java.util.List;

/**
 * 任务执行结束上报参数。
 * <p>仅供微服务 worker 上报使用：core 本地任务由本地执行器的任务完成/失败监听器处理。</p>
 *
 * @param taskId       任务id
 * @param workerId     执行者标识
 * @param status       任务最终状态，只允许 {@link com.sfc.task.AsyncTaskConstants.Status#FINISH}、
 *                     {@link com.sfc.task.AsyncTaskConstants.Status#FAILED}、
 *                     {@link com.sfc.task.AsyncTaskConstants.Status#CANCEL} 三者之一
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
