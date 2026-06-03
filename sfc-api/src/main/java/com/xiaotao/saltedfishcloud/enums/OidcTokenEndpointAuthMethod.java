package com.xiaotao.saltedfishcloud.enums;

/**
 * OIDC Token 端点客户端认证方式。
 * <p>
 * 对应 OpenID Connect Core 1.0 中 token_endpoint_auth_method 参数的取值。
 * </p>
 */
public enum OidcTokenEndpointAuthMethod {

    /**
     * 使用 HTTP Basic 认证，将 client_id 和 client_secret 编码后放入 Authorization 请求头。
     */
    CLIENT_SECRET_BASIC,

    /**
     * 将 client_id 和 client_secret 作为表单参数放在请求体中发送。
     */
    CLIENT_SECRET_POST,

    /**
     * 不使用客户端凭证认证（适用于公开客户端，通常配合 PKCE 使用）。
     */
    NONE
}
