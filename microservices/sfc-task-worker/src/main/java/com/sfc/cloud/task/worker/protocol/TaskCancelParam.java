package com.sfc.cloud.task.worker.protocol;

/**
 * 取消任务的请求参数（调用方 → core）。
 *
 * <p>这是<b>出站</b>报文：调用方（通常是微服务）向 {@code POST /api/internal/task/cancel} 发起请求，
 * 请求体即为本 record。core 对等待中的任务直接置为已取消（不会再被执行者认领），
 * 对运行中的任务按服务发现定位执行者实例地址后下发中断指令。</p>
 *
 * <p>仅接受服务间内部调用（由 sfc-auth 校验内部调用凭证）。</p>
 *
 * @param taskId 要取消的任务id，不能为空
 */
public record TaskCancelParam(Long taskId) {
}
