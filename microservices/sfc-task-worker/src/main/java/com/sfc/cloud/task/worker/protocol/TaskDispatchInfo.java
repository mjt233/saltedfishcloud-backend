package com.sfc.cloud.task.worker.protocol;

import java.util.Date;

/**
 * 下发给执行者的任务信息（core → 执行者）。
 *
 * @param id          任务id
 * @param name        任务名称
 * @param taskType    任务类型
 * @param params      任务参数（JSON 或纯文本，由任务实现自行解析）
 * @param uid         任务创建者用户id
 * @param cpuOverhead 任务的 CPU 开销指数
 * @param retryCount  已被故障转移重试的次数
 * @param createAt    任务创建时间
 */
public record TaskDispatchInfo(
        Long id,
        String name,
        String taskType,
        String params,
        Long uid,
        Integer cpuOverhead,
        Integer retryCount,
        Date createAt
) {
}
