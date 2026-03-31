package com.sfc.task.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 异步任务创建参数
 */
@Getter
@Setter
public class AsyncTaskCreateParam {

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务参数
     */
    private String params;

    /**
     * CPU负荷指数
     */
    private Integer cpuOverhead;

    /**
     * 是否为临时任务
     */
    private Boolean isTemp;

    /**
     * 是否立即执行
     */
    private Boolean immediate;
}
