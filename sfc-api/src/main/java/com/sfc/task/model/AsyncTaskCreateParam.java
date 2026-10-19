package com.sfc.task.model;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * 异步任务创建参数
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsyncTaskCreateParam {

    /**
     * 任务名称
     */
    @NotBlank
    private String name;

    /**
     * 任务类型
     */
    @NotBlank
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
