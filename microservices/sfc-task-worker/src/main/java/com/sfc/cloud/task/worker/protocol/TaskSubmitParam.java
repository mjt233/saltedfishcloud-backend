package com.sfc.cloud.task.worker.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 服务间提交任务请求参数（调用方 → core）。
 *
 * <p>仅接受服务间内部调用（由 sfc-auth 校验内部调用凭证）；{@code uid} 表示以哪个用户的身份创建任务，
 * 由调用方显式指定。</p>
 *
 * @param name        任务名称
 * @param taskType    任务类型，须与某个执行者声明的任务类型一致
 * @param params      任务参数
 * @param uid         任务归属用户id
 * @param cpuOverhead 任务的 CPU 开销指数
 * @param isTemp      是否为临时任务
 * @param immediate   是否要求立即处理（紧急任务）。为 true 时任务进入紧急通道，
 *                    执行者满载时仍会优先认领并执行
 */
public record TaskSubmitParam(
        String name,
        String taskType,
        String params,
        Long uid,
        Integer cpuOverhead,
        @JsonProperty("isTemp") Boolean isTemp,
        Boolean immediate
) {
}
