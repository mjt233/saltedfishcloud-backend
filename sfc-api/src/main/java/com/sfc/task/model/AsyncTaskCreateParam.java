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
     * 是否立即执行。
     * <p>为 {@code true} 时任务以紧急优先级入队（{@link com.sfc.task.AsyncTaskConstants.Priority#URGENT}），
     * 执行者满载时也会优先认领执行；否则按普通优先级排队。</p>
     */
    private Boolean immediate;
}
