package com.sfc.cloud.task.worker.protocol;

/**
 * worker 注销请求参数，镜像 core 的 {@code com.sfc.task.protocol.TaskWorkerUnregisterParam}。
 *
 * @param workerId worker 唯一标识
 */
public record TaskWorkerUnregisterParam(String workerId) {
}
