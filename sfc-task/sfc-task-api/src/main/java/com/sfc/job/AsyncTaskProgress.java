package com.sfc.job;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 异步任务的执行进度
 */
@Data
@Accessors(chain = true)
public class AsyncTaskProgress {

    /**
     * 目标完成量，-1为未知
     */
    private long loaded;

    /**
     * 已完成的量
     */
    private long total;

    /**
     * 速度的上一次记录时间（Unix时间戳 毫秒）
     */
    private long lastUpdateTime;

    /**
     * 每毫秒完成的量
     */
    private long speed;
}
