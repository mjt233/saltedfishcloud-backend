package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将系统内部的 {@link ThirdPartyAppAuthorizationService} 适配为 Spring Authorization Server 的
 * {@link OAuth2AuthorizationConsentService}。
 *
 * <p>授权同意对象 {@link OAuth2AuthorizationConsent} 与 {@link ThirdPartyAppAuthorization} 的映射关系：
 * <ul>
 *   <li>{@code registeredClientId} ↔ {@link ThirdPartyAppAuthorization#getAppId()} 的字符串形式</li>
 *   <li>{@code principalName} ↔ {@link ThirdPartyAppAuthorization#getUid()} 的字符串形式</li>
 *   <li>scope 由 {@code SCOPE_} 前缀的 {@link GrantedAuthority} 提取并合并</li>
 * </ul>
 * </p>
 */
@RequiredArgsConstructor
public class OidcAuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final ThirdPartyAppAuthorizationService authorizationService;

    /**
     * {@inheritDoc}
     *
     * <p>从 {@link OAuth2AuthorizationConsent} 中提取以 {@code SCOPE_} 开头的授权项，
     * 去除前缀后合并为空格分隔的 scope 字符串，并委托给
     * {@link ThirdPartyAppAuthorizationService#authorize(Long, Long, String)} 持久化。
     * 该方法采用追加合并语义：不会覆盖已有的授权范围，而是将新 scope 与现有 scope 合并去重。</p>
     *
     * @param consent 待保存的授权同意对象
     */
    @Override
    public void save(OAuth2AuthorizationConsent consent) {
        Long appId = parseIdentifier(consent.getRegisteredClientId(), "registeredClientId");
        Long uid = parseIdentifier(consent.getPrincipalName(), "principalName");
        Set<String> scopes = consent.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("SCOPE_"))
                .map(a -> a.substring("SCOPE_".length()))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (scopes.isEmpty()) {
            // 旧授权模型没有“空 scope 但仍保留授权记录”的状态；因此这里显式对齐为整体撤销。
            authorizationService.revoke(appId, uid);
            return;
        }

        authorizationService.authorize(appId, uid, String.join(" ", scopes));
    }

    /**
     * {@inheritDoc}
     *
     * <p>撤销指定用户对指定应用的全部授权，委托给
     * {@link ThirdPartyAppAuthorizationService#revoke(Long, Long)}。</p>
     *
     * @param consent 待移除的授权同意对象
     */
    @Override
    public void remove(OAuth2AuthorizationConsent consent) {
        authorizationService.revoke(
                parseIdentifier(consent.getRegisteredClientId(), "registeredClientId"),
                parseIdentifier(consent.getPrincipalName(), "principalName")
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>根据 registeredClientId（appId）和 principalName（uid）查询授权记录，
     * 并将其映射为 {@link OAuth2AuthorizationConsent}。
     * 若不存在授权记录则返回 {@code null}。</p>
     *
     * @param registeredClientId 注册客户端 ID（即 appId 的字符串形式）
     * @param principalName      用户标识（即 uid 的字符串形式）
     * @return 对应的 {@link OAuth2AuthorizationConsent}，不存在时返回 {@code null}
     */
    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        ThirdPartyAppUserAuthorizationVo vo = authorizationService.getUserAppAuthorization(
                parseIdentifier(registeredClientId, "registeredClientId"),
                parseIdentifier(principalName, "principalName")
        );

        ThirdPartyAppAuthorization authorization = vo == null ? null : vo.getAuthorization();
        if (authorization == null || authorization.getScope() == null || authorization.getScope().isBlank()) {
            return null;
        }

        OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent
                .withId(registeredClientId, principalName);

        for (String scope : authorization.getScope().split(" ")) {
            String trimmed = scope.trim();
            if (!trimmed.isEmpty()) {
                builder.scope(trimmed);
            }
        }

        return builder.build();
    }

    /**
     * 将适配器收到的字符串标识解析为 Long 类型。
     *
     * @param identifier 标识字符串
     * @param fieldName  字段名称，用于拼装异常消息
     * @return 解析后的 Long 标识
     * @throws IllegalArgumentException 当标识为空、空白或不是十进制数字时抛出
     */
    private Long parseIdentifier(String identifier, String fieldName) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        try {
            return Long.valueOf(identifier);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " 必须是数字字符串", e);
        }
    }
}
