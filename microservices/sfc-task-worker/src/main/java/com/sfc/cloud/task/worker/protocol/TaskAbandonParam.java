package com.sfc.cloud.task.worker.protocol;

/**
 * 任务执行权放弃上报参数（执行者 → core）。
 *
 * <p>执行者因停机或主动放弃而不再执行任务时上报，core 会把任务重置回等待中状态
 * （累加重试次数）并重新发布，交给其他具备能力的执行者接管。</p>
 *
 * @param taskId   任务id
 * @param workerId 执行者标识
 * @param message  放弃原因说明
 */
public record TaskAbandonParam(Long taskId, String workerId, String message) {
}
