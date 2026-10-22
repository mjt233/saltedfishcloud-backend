package com.sfc.task.protocol;

/**
 * 服务间提交任务请求参数。
 * <p>该接口只接受服务间内部调用（由 sfc-auth 校验内部调用凭证），因此 {@code uid} 表示
 * 「以哪个用户的身份创建该任务」，由调用方显式指定。</p>
 *
 * @param name        任务名称
 * @param taskType    任务类型，须与某个执行者注册支持的任务类型一致
 * @param params      任务参数
 * @param uid         任务归属用户id
 * @param cpuOverhead 任务的 CPU 开销指数
 * @param isTemp      是否为临时任务
 */
public record TaskSubmitParam(
        String name,
        String taskType,
        String params,
        Long uid,
        Integer cpuOverhead,
        Boolean isTemp
) {
}
