package com.sfc.cloud.data.identity;

/**
 * 当前登录用户身份的提供者 SPI。
 *
 * <p>宿主侧与微服务侧的身份来源本质不同，本接口把差异挡在共享模块之外：</p>
 * <ul>
 *   <li><b>宿主（sfc-core）</b>：实现委托 Spring Security 上下文
 *       （{@code com.xiaotao.saltedfishcloud.utils.SecureUtils}）；</li>
 *   <li><b>微服务</b>：实现读取网关注入的信任头
 *       （{@link com.sfc.cloud.trust.SfcTrustHeaders#UID}）；管理员判定另经远程接口回源到 core。</li>
 * </ul>
 *
 * <p>因此本模块只依赖抽象，不引入 spring-security 与 servlet 依赖，保持"零宿主依赖"。</p>
 */
public interface CurrentUserProvider {

    /**
     * 获取当前登录用户的 uid。
     *
     * @return 当前用户 uid；未登录（匿名）时返回 {@code null}
     */
    Long getCurrentUid();

    /**
     * 判断当前登录用户是否为系统管理员。
     *
     * @return 当前用户是管理员返回 {@code true}；未登录或非管理员返回 {@code false}
     */
    boolean currentIsAdmin();
}
