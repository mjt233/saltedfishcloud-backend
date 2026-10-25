package com.sfc.cloud.task.worker.protocol;

import java.util.List;

/**
 * 批量查询任务状态与进度的请求参数（调用方 → core）。
 *
 * <p>这是<b>出站</b>报文：调用方（通常是微服务）向 {@code POST /api/internal/task/query} 发起请求，
 * 请求体即为本 record；core 按 {@link #taskIds()} 批量返回任务状态与最新进度快照，
 * 供微服务校准本地保存的任务状态镜像。</p>
 *
 * <p>仅接受服务间内部调用（由 sfc-auth 校验内部调用凭证）。</p>
 *
 * @param taskIds 要查询的任务id列表，不能为空且单次最多 500 个（core 侧硬限制，超出返回 400）
 */
public record TaskStateQueryParam(List<Long> taskIds) {
}
