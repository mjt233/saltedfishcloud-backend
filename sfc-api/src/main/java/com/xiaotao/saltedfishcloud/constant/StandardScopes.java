package com.xiaotao.saltedfishcloud.constant;

/**
 * 系统标准 OAuth2 授权范围（Scope）常量。
 * <p>
 * 与 {@link com.xiaotao.saltedfishcloud.enums.OpenAuthorizationScope 枚举} 对应，
 * 提供 {@code @RequireScope} 注解可直接引用的字符串常量。
 * </p>
 *
 * @see com.xiaotao.saltedfishcloud.annotations.RequireScope
 * @see com.xiaotao.saltedfishcloud.enums.OpenAuthorizationScope
 */
public final class StandardScopes {

    private StandardScopes() {
    }

    /**
     * OIDC 标准 scope，表示请求 OpenID Connect 身份验证。
     */
    public static final String OPENID = "openid";

    /**
     * 个人信息读取
     */
    public static final String PROFILE = "profile";

    /**
     * 私人网盘数据读取
     */
    public static final String STORAGE_READ = "storage_read";

    /**
     * 私人网盘数据修改
     */
    public static final String STORAGE_WRITE = "storage_write";
}
