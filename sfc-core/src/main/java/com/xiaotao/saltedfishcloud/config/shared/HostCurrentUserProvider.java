package com.xiaotao.saltedfishcloud.config.shared;

import com.sfc.cloud.data.identity.CurrentUserProvider;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;

/**
 * 宿主侧的身份提供者实现：委托 Spring Security 上下文。
 *
 * <p>共享基座（{@code sfc-common-data}）把"当前用户是谁"抽成了 SPI，
 * 宿主与微服务各自提供实现；宿主的既有口径来自 {@link SecureUtils}，
 * 本类只做一层薄适配，不改变任何判定语义。</p>
 *
 * @see com.sfc.cloud.data.identity.CurrentUserHolder
 */
public class HostCurrentUserProvider implements CurrentUserProvider {

    /**
     * 获取当前登录用户 uid。
     *
     * @return 当前用户 uid；未登录时返回 {@code null}
     */
    @Override
    public Long getCurrentUid() {
        return SecureUtils.getCurrentUid();
    }

    /**
     * 判断当前登录用户是否为系统管理员。
     *
     * @return 是管理员返回 {@code true}
     */
    @Override
    public boolean currentIsAdmin() {
        return SecureUtils.currentIsAdmin();
    }
}
