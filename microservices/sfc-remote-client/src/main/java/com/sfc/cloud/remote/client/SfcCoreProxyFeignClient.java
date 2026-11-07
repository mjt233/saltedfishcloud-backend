package com.sfc.cloud.remote.client;

import com.sfc.cloud.constants.SfcServiceName;
import com.sfc.cloud.model.SfcProxyInfo;
import com.sfc.common.model.json.SfcJsonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * sfc-core 代理节点查询客户端（Feign 声明式接口）。
 *
 * <p>所有请求都落在 core 的内部接口前缀 {@link #PREFIX} 下，由
 * {@link SfcRemoteClientAutoConfiguration} 注册的内部调用凭证拦截器
 * （{@code com.sfc.cloud.auth.client.SfcInternalTokenFeignInterceptor}）逐请求签发 JWT 凭证。</p>
 *
 * <p>{@code contextId} 必须与其它 Feign 客户端（如 {@code sfcCoreFileClient}、
 * {@code sfcCoreConfigClient}）区分开，否则 Spring Cloud 会因同名 Feign 客户端注册冲突而启动失败。</p>
 *
 * @see SfcCoreProxyClient 对外的统一调用入口
 */
@FeignClient(
        name = SfcServiceName.CORE,
        contextId = "sfcCoreProxyClient",
        path = SfcCoreProxyFeignClient.PREFIX
)
public interface SfcCoreProxyFeignClient {

    /** 接口前缀 */
    String PREFIX = "/api/internal/proxy";

    /**
     * 按用户查询可用代理节点。
     *
     * <p>core 侧返回该用户可见的代理：公共代理（{@code uid=0}）＋ 该用户私有代理，按 id 去重；
     * 非管理员不会返回受保护的公共代理。</p>
     *
     * @param uid 目标用户 ID，0 表示匿名/公共用户（只返回公共代理）
     * @return core 的响应信封，业务数据为该用户可见的代理节点列表（可能为空列表）
     */
    @GetMapping("available")
    SfcJsonResult<List<SfcProxyInfo>> listAvailable(@RequestParam("uid") long uid);
}
