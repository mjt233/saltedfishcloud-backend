package com.sfc.download.constant;

/**
 * 离线下载任务常量。
 *
 * <p>本服务只维护「下载任务」自身的工程语义；异步任务的调度状态由 core 统一管理，
 * 本服务通过 {@link Status} 中的镜像常量与之对齐。</p>
 */
public interface DownloadTaskConstants {

    /**
     * 异步任务类型标识：本服务作为执行者声明的能力，也是提交任务时使用的类型。
     *
     * <p>与旧插件 {@code AsyncTaskType.OFFLINE_DOWNLOAD} 的取值保持一致，便于任务在两者之间平滑切换。</p>
     */
    String TASK_TYPE = "offline-download";

    /** 提交到 core 的异步任务名称 */
    String TASK_NAME = "离线下载";

    /** 日志前缀 */
    String LOG_PREFIX = "[Download]";

    /**
     * 下载任务状态镜像常量。
     *
     * <p>取值与 core 的 {@code com.sfc.task.AsyncTaskConstants.Status} 完全一致，用于把异步任务的
     * 调度状态镜像到本服务的 {@code download_task.status} 列（列表查询、失败筛选与部署排障都依赖它）。</p>
     */
    interface Status {
        /** 在队列中等待 */
        Integer WAITING = 0;

        /** 运行中 */
        Integer RUNNING = 1;

        /** 已完成 */
        Integer FINISH = 2;

        /** 执行失败 */
        Integer FAILED = 3;

        /** 被取消 / 中断 */
        Integer CANCEL = 4;

        /** 执行节点离线 */
        Integer OFFLINE = 5;
    }
}
