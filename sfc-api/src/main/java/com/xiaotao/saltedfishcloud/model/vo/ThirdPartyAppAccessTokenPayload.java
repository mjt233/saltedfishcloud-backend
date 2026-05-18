package com.xiaotao.saltedfishcloud.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第三方 OAuth Access Token 载荷。
 * <p>
 * 该载荷用于在 Access Token 中内嵌应用与用户上下文，
 * 以便后续换取 ApiTicket 时无需再由调用方显式传入 {@code appId} 与 {@code uid}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThirdPartyAppAccessTokenPayload {

    /**
     * 第三方 OAuth 应用 ID。
     */
    private Long appId;

    /**
     * 已授权的系统用户 ID。
     */
    private Long uid;

    /**
     * 本次签发的 Access Token 唯一标识。
     */
    private String tokenId;
}
