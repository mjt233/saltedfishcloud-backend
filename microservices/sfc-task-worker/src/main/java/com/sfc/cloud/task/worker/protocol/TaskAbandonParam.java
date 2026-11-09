package com.sfc.cloud.task.worker.protocol;

/**
 * 任务执行权放弃上报参数（执行者 → core）。
 *
 * <p>执行者因停机或主动放弃而不再执行任务时上报，core 会把任务重置回等待中状态
 * 并重新发布，交给其他具备能力的执行者接管。</p>
 *
 * @param taskId   任务id
 * @param workerId 执行者标识
 * @param message  放弃原因说明
 * @param detached 是否为「挂起」语义：true 表示执行者停机但任务现场已保存到检查点、可由其他执行者接管，
 *                 core 重新排队时<b>不消耗</b>故障转移重试次数并保留检查点；null/false 按普通放弃处理
 *                 （累加重试次数）
 */
public record TaskAbandonParam(Long taskId, String workerId, String message, Boolean detached) {
}
