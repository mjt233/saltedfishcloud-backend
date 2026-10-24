package com.sfc.cloud.task.worker.protocol;

/**
 * 任务中断指令（core → 执行者）。
 *
 * <p>这是<b>入站</b>报文：core 按服务发现定位到执行任务的实例地址后，向
 * {@code POST /api/internal/task/interrupt} 发起请求，请求体即为本 record。</p>
 *
 * @param taskId 要中断的任务id
 */
public record TaskInterruptCommand(Long taskId) {
}
