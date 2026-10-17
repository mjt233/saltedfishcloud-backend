package com.sfc.cloud.remote.client;

import com.sfc.common.model.UserPrincipal;
import com.sfc.common.model.json.SfcJsonResult;

import java.util.Objects;

/**
 * sfc-core 用户身份查询统一客户端
 *
 * <p>微服务侧没有 Spring Security 上下文，"当前用户是谁、是不是管理员"只能回源到 core，
 * 本客户端即该能力的唯一入口；请求的凭证透传由自动装配挂在 Feign 链路上。</p>
 *
 * @see SfcCoreUserFeignClient Feign 链路实现
 */
public class SfcCoreUserClient {

    /** Feign 链路实现：获取当前登录用户 */
    private final SfcCoreUserFeignClient feignClient;

    /**
     * 构造统一客户端，由自动装配负责组装（包内可见：消费方不应自行构造）。
     *
     * @param feignClient Feign 链路实现
     */
    SfcCoreUserClient(SfcCoreUserFeignClient feignClient) {
        this.feignClient = Objects.requireNonNull(feignClient, "feignClient 不能为 null");
    }

    /**
     * 获取当前登录用户（含管理员判定）。
     *
     * <p>身份来自调用链路上透传的用户凭证，没有登录态时 core 返回 401；返回的管理员判定结果为
     * {@link UserPrincipal#isAdmin()}，可直接用于公共网盘（{@code uid=0}）等需要管理员权限的操作。</p>
     *
     * @return core 的响应信封，业务数据为当前用户主体（含管理员判定结果）
     */
    public SfcJsonResult<UserPrincipal> getCurrentUser() {
        return feignClient.getCurrentUser();
    }
}
