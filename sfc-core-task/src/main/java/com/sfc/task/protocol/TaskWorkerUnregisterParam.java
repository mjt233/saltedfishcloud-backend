package com.sfc.task.protocol;

/**
 * worker 注销请求参数。
 *
 * @param workerId worker 唯一标识
 */
public record TaskWorkerUnregisterParam(String workerId) {
}
