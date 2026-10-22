package com.sfc.task.protocol;

import com.sfc.task.model.AsyncTaskRecord;

import java.util.Date;

/**
 * 下发给执行者的任务信息。
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
    /**
     * 由任务记录构建下发信息。
     *
     * @param record 任务记录
     * @return 下发信息
     */
    public static TaskDispatchInfo from(AsyncTaskRecord record) {
        return new TaskDispatchInfo(
                record.getId(),
                record.getName(),
                record.getTaskType(),
                record.getParams(),
                record.getUid(),
                record.getCpuOverhead(),
                record.getRetryCount(),
                record.getCreateAt()
        );
    }
}
