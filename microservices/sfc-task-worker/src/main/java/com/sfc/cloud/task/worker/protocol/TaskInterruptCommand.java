package com.sfc.cloud.task.worker.protocol;

/**
 * 任务中断指令，镜像 core 的 {@code com.sfc.task.protocol.TaskInterruptCommand}。
 *
 * <p>这是<b>入站</b>报文：core 向 worker 的 {@code callbackUrl} 发起
 * {@code POST /api/internal/task/interrupt}，请求体即为本 record。</p>
 *
 * @param taskId 要中断的任务id
 */
public record TaskInterruptCommand(Long taskId) {
}
