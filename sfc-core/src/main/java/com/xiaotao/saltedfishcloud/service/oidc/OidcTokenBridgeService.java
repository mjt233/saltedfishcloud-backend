package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppAccessTokenPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import lombok.RequiredArgsConstructor;

/**
 * OIDC Token 桥接服务。
 * <p>
 * 封装 {@link ThirdPartyAppApiTicketService} 与 {@link ThirdPartyAppTokenService}，
 * 为 OIDC token 生成流程提供统一的调用入口：
 * <ul>
 *   <li>短期 ApiTicket → OIDC {@code access_token}（15 分钟）</li>
 *   <li>遗留 Access Token → OIDC {@code refresh_token}（长期有效）</li>
 * </ul>
 * </p>
 * <p>该类以 {@code @Bean} 方式注册，不使用 {@code @Service} 注解，
 * 由 {@link com.xiaotao.saltedfishcloud.config.security.oidc.OidcAuthorizationServerConfig} 统一管理。</p>
 */
@RequiredArgsConstructor
public class OidcTokenBridgeService {

    private final ThirdPartyAppApiTicketService apiTicketService;
    private final ThirdPartyAppTokenService tokenService;

    /**
     * 为指定应用与用户签发短期 ApiTicket，用作 OIDC {@code access_token}。
     * <p>
     * 固定使用 {@code permanent=false}（15 分钟短期票据）和 {@code revokeOlder=true}（自动撤销旧票据）。
     * </p>
     *
     * @param appId 第三方 OAuth 应用 ID
     * @param uid   系统用户 ID
     * @param scope 已授权的 scope，多个 scope 以空格分隔
     * @return 签发的 ApiTicket JWT 字符串
     */
    public String issueApiTicket(Long appId, Long uid, String scope) {
        ThirdPartyAppApiTicketPayload payload = ThirdPartyAppApiTicketPayload.builder()
                .appId(appId)
                .uid(uid)
                .scope(scope)
                .permanent(false)
                .build();
        return apiTicketService.issue(payload, true);
    }

    /**
     * 为指定应用与用户签发遗留格式 Access Token，用作 OIDC {@code refresh_token}。
     *
     * @param appId 第三方 OAuth 应用 ID
     * @param uid   系统用户 ID
     * @return 遗留格式的 Access Token JWT 字符串
     * @see ThirdPartyAppTokenService#issueLegacyAccessToken(Long, Long)
     */
    public String issueLegacyAccessToken(Long appId, Long uid) {
        return tokenService.issueLegacyAccessToken(appId, uid);
    }

    /**
     * 验证遗留格式 Access Token 并返回其载荷。
     *
     * @param accessToken 遗留格式的 Access Token JWT 字符串
     * @return 解析出的载荷对象
     * @throws com.xiaotao.saltedfishcloud.exception.JsonException 当 token 格式无效时抛出
     */
    public ThirdPartyAppAccessTokenPayload validateLegacyAccessToken(String accessToken) {
        return tokenService.validateLegacyAccessToken(accessToken);
    }

    /**
     * 撤销指定应用与用户的所有遗留 token 及 ApiTicket。
     *
     * @param appId 第三方 OAuth 应用 ID
     * @param uid   系统用户 ID
     */
    public void revokeTokens(Long appId, Long uid) {
        tokenService.revoke(appId, uid);
    }

    /**
     * 解析并验证 ApiTicket 字符串，返回其载荷。
     *
     * @param apiTicket ApiTicket JWT 字符串
     * @return 解析出的 ApiTicket 载荷对象
     */
    public ThirdPartyAppApiTicketPayload parseApiTicket(String apiTicket) {
        return apiTicketService.parseAndValidateApiTicket(apiTicket);
    }

    /**
     * 根据授权码从缓存中获取授权数据。
     *
     * @param code 授权码
     * @return 授权数据，授权码无效或已过期时返回 {@code null}
     */
    public ThirdPartyAppUserAuthorizationVo getAuthorizationCodeData(String code) {
        return tokenService.getAuthorizationCodeData(code);
    }
}
