package com.sfc.cloud.remote.client;

import com.sfc.common.model.UserPrincipal;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.cloud.constants.SfcServiceName;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * sfc-core 用户身份查询客户端（Feign 声明式接口）
 *
 * <p>与 {@link SfcCoreFileFeignClient} 不同，本接口走的是 core 的<b>公开</b>接口
 * {@code GET /api/user}（而不是 {@code /api/internal/**}），因此不拼接内部接口前缀，
 * {@code contextId} 也必须与文件客户端区分开，否则 Spring Cloud 会因同名 Feign 客户端
 * 注册冲突而启动失败。</p>
 *
 * <p>调用链路上的凭证由 {@link SfcRemoteClientAutoConfiguration} 注册的
 * {@code com.sfc.cloud.auth.client.SfcInternalTokenFeignInterceptor}（服务身份）与
 * {@code FeignCredentialForwardingInterceptor}（透传入站请求的用户凭证）逐请求带上；
 * core 依据用户凭证解析"当前用户"，并在响应里回填管理员判定结果
 * （{@link UserPrincipal#isAdmin()} 序列化出的 {@code admin} 字段）。没有用户登录态时
 * core 返回 401。</p>
 *
 * <p>core 的响应体带 Jackson 多态类型字段 {@code @class}（宿主 {@code UserPrincipal} 标注了
 * {@code @JsonTypeInfo(Id.CLASS)}），而共享模型 {@link UserPrincipal} 未声明该注解，
 * Boot 默认忽略未知属性，因此可直接反序列化。</p>
 *
 * @see SfcCoreUserClient 对外的统一调用入口
 */
@FeignClient(
        name = SfcServiceName.CORE,
        contextId = "sfcCoreUserClient",
        path = ""
)
public interface SfcCoreUserFeignClient {

    /**
     * 获取当前登录用户。
     *
     * @return core 的响应信封，业务数据为当前用户主体（含管理员判定结果）
     */
    @GetMapping("/api/user")
    SfcJsonResult<UserPrincipal> getCurrentUser();
}
