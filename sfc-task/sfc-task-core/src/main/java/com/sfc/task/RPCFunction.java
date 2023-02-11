package com.sfc.task;

/**
 * RPC调用函数名称常量
 */
public interface RPCFunction {
    /**
     * 获取执行中的异步任务日志
     */
    String TASK_GET_LOG = "task_get_log";

    /**
     * 中断任务
     */
    String TASK_INTERRUPT = "task_interrupt";
}
