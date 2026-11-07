package com.sfc.download.identity;

import com.sfc.cloud.data.identity.CurrentUserProvider;

/**
 * 离线下载微服务侧的身份提供者实现（{@link CurrentUserProvider} SPI）。
 *
 * <p>共享基座 {@code sfc-common-data} 把「当前用户是谁」抽象成 SPI：宿主委托 Spring Security，
 * 微服务则委托 {@link SfcCurrentUserSupport}（网关注入的信任头 {@code X-Sfc-Uid}）。
 * 本类只做一层薄适配，不引入任何额外判定语义。</p>
 *
 * <p><b>为什么必须注册：</b>{@code SfcUIDValidator} / {@code SfcCrudServiceImpl} / {@code SfcDBUtils}
 * 都是静态工具，身份只能经 {@link com.sfc.cloud.data.identity.CurrentUserHolder} 取得；
 * 未注册时 SPI 处于 fail-closed 状态（uid 恒为 {@code null}、admin 恒为 {@code false}），
 * 控制器上的 uid 校验会被<b>全部拒绝</b>。注册见 {@link SfcDownloadIdentityConfiguration}。</p>
 *
 * @see SfcCurrentUserSupport
 * @see SfcDownloadIdentityConfiguration
 */
public class SfcCurrentUserProvider implements CurrentUserProvider {

    /**
     * {@inheritDoc}
     *
     * @return 当前登录用户 uid；未登录或无 HTTP 上下文时返回 {@code null}
     */
    @Override
    public Long getCurrentUid() {
        return SfcCurrentUserSupport.getCurrentUid();
    }

    /**
     * {@inheritDoc}
     *
     * @return 是管理员返回 {@code true}；未登录、无法回源到 core 时返回 {@code false}
     */
    @Override
    public boolean currentIsAdmin() {
        return SfcCurrentUserSupport.currentIsAdmin();
    }
}
