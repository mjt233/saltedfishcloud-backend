package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppKeyRepo;
import com.xiaotao.saltedfishcloud.enums.OidcTokenEndpointAuthMethod;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.HashSet;
import java.util.Set;

/**
 * 将系统内部的 {@link ThirdPartyApp} 元数据适配为 Spring Authorization Server 的
 * {@link RegisteredClientRepository}。
 *
 * <p>内部注册 ID 与 clientId 均使用 {@link ThirdPartyApp#getId()} 的字符串形式，
 * 查找时需保证传入的字符串可解析为 {@code Long}。</p>
 *
 * <p>仅当应用同时满足以下条件才会被暴露为注册客户端：
 * <ul>
 *   <li>{@link ThirdPartyApp#getIsEnabled()} 为 {@code true}</li>
 *   <li>{@link ThirdPartyApp#getOidcEnabled()} 为 {@code true}</li>
 * </ul>
 * </p>
 */
public class OidcRegisteredClientRepository implements RegisteredClientRepository {

    private final ThirdPartyAppService appService;
    private final ThirdPartyAppKeyRepo keyRepo;
    private final Set<String> availableScopes;

    /**
     * 创建 OIDC 注册客户端仓库。
     *
     * @param appService      第三方应用服务
     * @param keyRepo         第三方应用密钥仓库
     * @param availableScopes 系统可用的所有 OAuth2 scope 集合（含 openid）
     */
    public OidcRegisteredClientRepository(ThirdPartyAppService appService,
                                          ThirdPartyAppKeyRepo keyRepo,
                                          Set<String> availableScopes) {
        this.appService = appService;
        this.keyRepo = keyRepo;
        this.availableScopes = availableScopes;
    }

    /**
     * {@inheritDoc}
     *
     * <p>根据内部注册 ID（即 appId 的字符串形式）查找对应的注册客户端。</p>
     *
     * @param id 注册客户端内部 ID（即 {@link ThirdPartyApp#getId()} 的字符串形式）
     * @return 映射后的 {@link RegisteredClient}，若应用不存在、未启用或未启用 OIDC 则返回 {@code null}
     */
    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>根据 clientId（即 appId 的字符串形式）查找并映射注册客户端。
     * 仅已启用且开启了 OIDC 支持的应用才会被映射。</p>
     *
     * @param clientId OAuth2 client_id 参数（即 {@link ThirdPartyApp#getId()} 的字符串形式）
     * @return 映射后的 {@link RegisteredClient}，若条件不满足则返回 {@code null}
     */
    @Override
    public RegisteredClient findByClientId(String clientId) {
        ThirdPartyApp app;
        try {
            app = appService.findById(Long.valueOf(clientId));
        } catch (NumberFormatException e) {
            return null;
        }

        if (app == null || !Boolean.TRUE.equals(app.getIsEnabled()) || !Boolean.TRUE.equals(app.getOidcEnabled())) {
            return null;
        }

        Set<String> redirectUris = new HashSet<>();
        if (StringUtils.hasText(app.getCallbackUrl())) {
            redirectUris.add(app.getCallbackUrl());
        }

        String clientSecret = keyRepo.findByAppId(app.getId()).stream()
                .findFirst()
                .map(ThirdPartyAppKey::getClientSecretHash)
                .orElse(null);

        ClientAuthenticationMethod authMethod = mapAuthMethod(app.getOidcTokenEndpointAuthMethod());

        return RegisteredClient.withId(app.getId().toString())
                .clientId(app.getId().toString())
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(authMethod)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(redirectUris))
                .scopes(scopes -> scopes.addAll(availableScopes))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(Boolean.TRUE.equals(app.getRequirePkce()))
                        .requireAuthorizationConsent(true)
                        .build())
                .build();
    }

    /**
     * 将系统内部的 {@link OidcTokenEndpointAuthMethod} 枚举值映射为
     * Spring Authorization Server 的 {@link ClientAuthenticationMethod}。
     *
     * @param method 系统内部的认证方式枚举
     * @return 对应的 Spring Authorization Server 认证方式
     */
    private ClientAuthenticationMethod mapAuthMethod(OidcTokenEndpointAuthMethod method) {
        if (method == null) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        }
        return switch (method) {
            case CLIENT_SECRET_POST -> ClientAuthenticationMethod.CLIENT_SECRET_POST;
            case NONE -> ClientAuthenticationMethod.NONE;
            default -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        };
    }

    /**
     * 本实现不支持通过接口保存客户端注册信息。
     * 客户端注册由系统管理后台独立维护，通过 {@link ThirdPartyApp} 实体持久化。
     *
     * @param registeredClient 注册客户端对象（忽略）
     * @throws UnsupportedOperationException 始终抛出，不支持此操作
     */
    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("客户端注册信息通过系统管理后台维护，不支持通过此接口保存");
    }
}
