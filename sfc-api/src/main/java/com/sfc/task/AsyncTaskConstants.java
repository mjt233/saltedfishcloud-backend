package com.sfc.task;

/**
 * 异步任务常量
 */
public interface AsyncTaskConstants {
    /**
     * 任务状态
     */
    interface Status {
        /**
         * 在队列中等待
         */
        Integer WAITING = 0;

        /**
         * 运行中
         */
        Integer RUNNING = 1;

        /**
         * 任务完成
         */
        Integer FINISH = 2;

        /**
         * 任务失败
         */
        Integer FAILED = 3;

        /**
         * 任务被取消/中断
         */
        Integer CANCEL = 4;

        /**
         * 任务离线（负责执行的节点掉线）
         */
        Integer OFFLINE = 5;
    }

    /**
     * 任务优先级。
     * <p>数值越大越优先被认领；{@link #URGENT} 及以上的任务在执行者满载时仍会被认领执行。</p>
     */
    interface Priority {
        /**
         * 普通优先级（默认）
         */
        Integer NORMAL = 0;

        /**
         * 紧急优先级（对应任务提交参数 immediate=true）
         */
        Integer URGENT = 100;
    }

    /**
     * 任务执行者角色。
     * <p>用于记录任务由哪类执行者认领（core 节点内嵌的执行者或微服务实例），供管理接口展示与排障使用。
     * 中断指令统一下发方式为「按服务发现定位实例地址后走内部 HTTP 接口」，与角色无关。</p>
     */
    interface WorkerRole {
        /**
         * core 节点（本进程或其他 core 节点）
         */
        String CORE = "CORE";

        /**
         * 微服务实例
         */
        String SERVICE = "SERVICE";
    }
}
