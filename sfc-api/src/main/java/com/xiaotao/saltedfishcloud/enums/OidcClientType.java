package com.xiaotao.saltedfishcloud.enums;

/**
 * OIDC 客户端类型。
 * <p>
 * 区分公开客户端（无法保密客户端凭证）与机密客户端（能够安全存储客户端凭证）。
 * 对应 RFC 6749 Section 2.1 中的 client type 定义。
 * </p>
 */
public enum OidcClientType {

    /**
     * 机密客户端：能够安全保密客户端凭证（如服务端应用）。
     */
    CONFIDENTIAL,

    /**
     * 公开客户端：无法安全保密客户端凭证（如单页应用、移动端应用）。
     */
    PUBLIC
}
