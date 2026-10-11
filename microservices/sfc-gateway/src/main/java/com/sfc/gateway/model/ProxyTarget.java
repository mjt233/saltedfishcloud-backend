package com.sfc.gateway.model;

/**
 * 代理目标：一次本地判定得出的"转到哪个服务、用哪条下游路径"。
 *
 * <p>
 * 这两个字段都是<b>网关自己算出来的</b>（判定与转发因此天然使用同一个字符串），
 * 不再来自 core 的 resolve 端点——该端点已随"网关独立验签"整体删除。
 * </p>
 *
 * @param serviceName 目标微服务名（已通过字符集校验、白名单与自我转发阻断）
 * @param targetPath  转发到微服务的完整下游路径（{@code sfc.api.prefix} ＋ 路径余部，逐字节保留原始写法）
 */
public record ProxyTarget(String serviceName, String targetPath) {
}
