package com.sfc.download.model.param;

/**
 * 下载任务列表的筛选类型。
 *
 * <p>由旧插件的 {@code com.xiaotao.saltedfishcloud.model.param.TaskType} 迁移而来：
 * 微服务不依赖宿主模块，因此在本服务内重新声明同语义枚举，取值与前端传参保持完全一致。</p>
 */
public enum TaskType {

    /**
     * 全部任务（不按状态过滤）。
     */
    ALL,

    /**
     * 下载中：对应异步任务状态「等待中」（0）与「运行中」（1）。
     */
    DOWNLOADING,

    /**
     * 已完成：对应异步任务状态「完成」（2）。
     */
    FINISH,

    /**
     * 失败：对应异步任务状态「失败」（3）、「取消」（4）与「离线」（5）。
     */
    FAILED
}
