package com.sfc.task.worker;

/**
 * 任务执行者（worker）类型。
 */
public enum TaskWorkerKind {
    /**
     * 咸鱼云 core 节点。任务由该节点的本地任务执行器执行。
     */
    LOCAL_NODE,

    /**
     * 微服务实例。任务由该实例通过 Worker SDK 执行。
     */
    REMOTE_SERVICE
}
