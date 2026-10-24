package com.sfc.cloud.task.worker.protocol;

import java.util.List;

/**
 * 任务认领请求参数（执行者 → core）。
 *
 * @param serviceId   执行者所属服务名（服务注册中心中的 serviceId）
 * @param workerId    执行者实例地址（{@code host:port}），用于任务归属、存活判定与中断寻址
 * @param role        执行者角色，微服务实例固定为 {@code SERVICE}
 * @param types       本次认领允许的任务类型集合，为空表示不认领任何任务
 * @param urgentOnly  是否只认领紧急任务（worker 满载时为 true）
 * @param waitSeconds 无任务可认领时的最长等待秒数（长轮询）。小于等于 0 表示立即返回
 */
public record TaskClaimParam(
        String serviceId,
        String workerId,
        String role,
        List<String> types,
        Boolean urgentOnly,
        Integer waitSeconds
) {
}
