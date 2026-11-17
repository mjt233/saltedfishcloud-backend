package com.sfc.cloud.task.worker.protocol;

/**
 * 任务检查点保存参数（执行者 → core）。
 *
 * <p>执行者在任务执行过程中把任务自定义的现场快照（JSON 文本）同步到 core，用于任务被挂起或
 * 执行者失联后由其他执行者接管时恢复现场。core 只做归属校验与持久化，不解析检查点内容。</p>
 *
 * @param taskId     任务id
 * @param workerId   执行者标识
 * @param taskType   任务类型
 * @param checkpoint 检查点内容（JSON 文本，语义由任务实现自定义）；为空白时 core 视为删除检查点
 */
public record TaskCheckpointSaveParam(Long taskId, String workerId, String taskType, String checkpoint) {
}
