package com.sfc.cloud.task.worker.protocol;

import java.util.List;

/**
 * 任务认领请求参数，镜像 core 的 {@code com.sfc.task.protocol.TaskClaimParam}。
 *
 * @param workerId    worker 唯一标识
 * @param types       本次认领允许的任务类型集合，为空表示不认领任何任务
 * @param waitSeconds 无任务可认领时的最长等待秒数（长轮询）。小于等于 0 表示立即返回
 */
public record TaskClaimParam(String workerId, List<String> types, Integer waitSeconds) {
}
