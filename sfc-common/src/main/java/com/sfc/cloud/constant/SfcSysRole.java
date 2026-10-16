package com.sfc.cloud.constant;

/**
 * 系统角色。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.constant.SysRole} 逐字迁移而来
 * （仅包名/接口名变更），角色常量值逐字保留——它们会写入令牌并在鉴权时比对，
 * 改动将导致鉴权失败。</p>
 */
public interface SfcSysRole {
    /**
     * 系统管理员
     */
    String ADMIN = "ADMIN";

    /**
     * 通过第三方OAuth应用授权的会话用户角色
     */
    String OAUTH_USER = "OAUTH_USER";
}
