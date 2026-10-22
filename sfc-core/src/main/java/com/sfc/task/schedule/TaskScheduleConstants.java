package com.sfc.task.schedule;

/**
 * 异步任务调度相关常量。
 */
public final class TaskScheduleConstants {

    /**
     * 任务故障转移的最大重试次数。<br>
     * 执行者失联或主动放弃导致任务被回收重发时累加，达到上限后任务直接置为失败，避免无限重试。
     */
    public static final int MAX_RETRY = 3;

    /**
     * 任务执行者注册信息有效期（秒）
     */
    public static final int WORKER_TTL_SECONDS = 30;

    private TaskScheduleConstants() {
    }
}
