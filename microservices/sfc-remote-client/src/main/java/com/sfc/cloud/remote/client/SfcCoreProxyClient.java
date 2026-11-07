package com.sfc.cloud.remote.client;

import com.sfc.cloud.model.SfcProxyInfo;
import com.sfc.common.model.json.SfcJsonResult;

import java.util.List;
import java.util.Objects;

/**
 * sfc-core 代理节点查询统一客户端。
 *
 * <p>供没有宿主用户上下文的微服务（如离线下载服务）按用户查询可用代理节点：调用方显式传入目标
 * 用户 ID，core 依据该 ID 计算可见范围（公共代理 + 本人代理；非管理员过滤受保护的公共代理）。</p>
 *
 * @see SfcCoreProxyFeignClient Feign 链路实现
 */
public class SfcCoreProxyClient {

    /** Feign 链路实现：按用户查询可用代理 */
    private final SfcCoreProxyFeignClient feignClient;

    /**
     * 构造统一客户端，由自动装配负责组装（包内可见：消费方不应自行构造）。
     *
     * @param feignClient Feign 链路实现
     */
    SfcCoreProxyClient(SfcCoreProxyFeignClient feignClient) {
        this.feignClient = Objects.requireNonNull(feignClient, "feignClient 不能为 null");
    }

    /**
     * 按用户查询可用代理节点。
     *
     * <p>返回的代理信息只包含连接代理所必需的字段（名称、协议类型、地址、端口），可直接用于
     * 构造 {@code java.net.Proxy}。调用方通常先按名称选中代理，再从结果中取类型/地址/端口。</p>
     *
     * @param uid 目标用户 ID，0 表示匿名/公共用户（只返回公共代理，且不含受保护的公共代理）
     * @return core 的响应信封，业务数据为该用户可见的代理节点列表（可能为空列表）
     */
    public SfcJsonResult<List<SfcProxyInfo>> listAvailable(long uid) {
        return feignClient.listAvailable(uid);
    }
}
