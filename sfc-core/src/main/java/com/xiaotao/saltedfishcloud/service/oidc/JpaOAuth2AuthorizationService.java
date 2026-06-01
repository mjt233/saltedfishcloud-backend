package com.xiaotao.saltedfishcloud.service.oidc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.dao.jpa.Oauth2AuthorizationRepo;
import com.xiaotao.saltedfishcloud.model.po.OAuth2AuthorizationEntity;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.lang.Nullable;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 JPA 的 {@link OAuth2AuthorizationService} 实现。
 * <p>
 * 将 {@link OAuth2Authorization} 持久化到 {@code oauth2_authorization} 表，
 * 服务重启后可完整恢复 authorization 状态（包括所有 token），解决
 * {@link org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService}
 * 重启丢失导致 refresh_token 流程 NPE 的问题。
 * </p>
 * <p>
 * 序列化策略与 {@link org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService}
 * 一致：使用 Jackson + {@link SecurityJackson2Modules} + {@link OAuth2AuthorizationServerJackson2Module}
 * 对 attributes 和 token metadata 进行 JSON 序列化。
 * </p>
 */
public class JpaOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private final Oauth2AuthorizationRepo repo;
    private final RegisteredClientRepository registeredClientRepository;

    /**
     * 构造 JPA 授权服务。
     *
     * @param repo                         授权实体仓库
     * @param registeredClientRepository   注册客户端仓库，用于重建 {@link RegisteredClient}
     */
    public JpaOAuth2AuthorizationService(Oauth2AuthorizationRepo repo,
                                         RegisteredClientRepository registeredClientRepository) {
        Assert.notNull(repo, "repo cannot be null");
        Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
        this.repo = repo;
        this.registeredClientRepository = registeredClientRepository;
    }

    /**
     * 构造并配置用于序列化 {@link OAuth2Authorization} attributes 与 token metadata 的 {@link ObjectMapper}。
     * <p>
     * 对齐 Spring Authorization Server 上游 {@code JdbcOAuth2AuthorizationService} 的实现：
     * </p>
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ClassLoader classLoader = JpaOAuth2AuthorizationService.class.getClassLoader();
        mapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        return mapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        OAuth2AuthorizationEntity entity = toEntity(authorization);
        repo.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        repo.deleteById(authorization.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return repo.findById(id).map(this::toObject).orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");
        Optional<OAuth2AuthorizationEntity> entity;
        if (tokenType == null) {
            entity = repo.findByUnknowTypeToken(token);
        } else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            entity = repo.findByState(token);
        } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            entity = repo.findByAuthorizationCodeValue(token);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            entity = repo.findByAccessTokenValue(token);
        } else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
            entity = repo.findByOidcIdTokenValue(token);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            entity = repo.findByRefreshTokenValue(token);
        } else if (OAuth2ParameterNames.USER_CODE.equals(tokenType.getValue())) {
            entity = repo.findByUserCodeValue(token);
        } else if (OAuth2ParameterNames.DEVICE_CODE.equals(tokenType.getValue())) {
            entity = repo.findByDeviceCodeValue(token);
        } else {
            return null;
        }
        return entity.map(this::toObject).orElse(null);
    }

    /**
     * 将 {@link OAuth2Authorization} 转换为 {@link OAuth2AuthorizationEntity}。
     *
     * @param authorization 授权对象
     * @return JPA 实体
     */
    private OAuth2AuthorizationEntity toEntity(OAuth2Authorization authorization) {
        OAuth2AuthorizationEntity entity = new OAuth2AuthorizationEntity();
        entity.setId(authorization.getId());
        entity.setRegisteredClientId(authorization.getRegisteredClientId());
        entity.setPrincipalName(authorization.getPrincipalName());
        entity.setAuthorizationGrantType(authorization.getAuthorizationGrantType().getValue());

        String authorizedScopes = null;
        if (!CollectionUtils.isEmpty(authorization.getAuthorizedScopes())) {
            authorizedScopes = StringUtils.collectionToDelimitedString(authorization.getAuthorizedScopes(), ",");
        }
        entity.setAuthorizedScopes(authorizedScopes);

        entity.setAttributes(writeMap(authorization.getAttributes()));

        String state = authorization.getAttribute(OAuth2ParameterNames.STATE);
        if (StringUtils.hasText(state)) {
            entity.setState(state);
        }

        writeToken(entity, authorization.getToken(OAuth2AuthorizationCode.class));
        writeAccessToken(entity, authorization.getToken(OAuth2AccessToken.class));
        writeOidcIdToken(entity, authorization.getToken(OidcIdToken.class));
        writeRefreshToken(entity, authorization.getRefreshToken());
        writeToken(entity, authorization.getToken(OAuth2UserCode.class));
        writeToken(entity, authorization.getToken(OAuth2DeviceCode.class));

        return entity;
    }

    /**
     * 将 {@link OAuth2AuthorizationEntity} 转换为 {@link OAuth2Authorization}。
     *
     * @param entity JPA 实体
     * @return 授权对象
     */
    private OAuth2Authorization toObject(OAuth2AuthorizationEntity entity) {
        RegisteredClient registeredClient = registeredClientRepository.findById(entity.getRegisteredClientId());
        if (registeredClient == null) {
            throw new DataRetrievalFailureException(
                    "The RegisteredClient with id '" + entity.getRegisteredClientId()
                    + "' was not found in the RegisteredClientRepository.");
        }

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient);
        builder.id(entity.getId())
                .principalName(entity.getPrincipalName())
                .authorizationGrantType(new AuthorizationGrantType(entity.getAuthorizationGrantType()));

        Set<String> authorizedScopes = Collections.emptySet();
        if (entity.getAuthorizedScopes() != null) {
            authorizedScopes = StringUtils.commaDelimitedListToSet(entity.getAuthorizedScopes());
        }
        builder.authorizedScopes(authorizedScopes);

        Map<String, Object> attributes = parseMap(entity.getAttributes());
        builder.attributes(attrs -> attrs.putAll(attributes));

        if (StringUtils.hasText(entity.getState())) {
            builder.attribute(OAuth2ParameterNames.STATE, entity.getState());
        }

        readAuthorizationCode(entity, builder);
        readAccessToken(entity, builder);
        readOidcIdToken(entity, builder);
        readRefreshToken(entity, builder);
        readUserCode(entity, builder);
        readDeviceCode(entity, builder);

        return builder.build();
    }

    private void writeToken(OAuth2AuthorizationEntity entity,
                            OAuth2Authorization.Token<?> token) {
        if (token == null) {
            return;
        }
        OAuth2AuthorizationCode code = (OAuth2AuthorizationCode) token.getToken();
        entity.setAuthorizationCodeValue(code.getTokenValue());
        entity.setAuthorizationCodeIssuedAt(code.getIssuedAt());
        entity.setAuthorizationCodeExpiresAt(code.getExpiresAt());
        entity.setAuthorizationCodeMetadata(writeMap(token.getMetadata()));
    }

    private void writeAccessToken(OAuth2AuthorizationEntity entity,
                                  OAuth2Authorization.Token<OAuth2AccessToken> token) {
        if (token == null) {
            return;
        }
        OAuth2AccessToken accessToken = token.getToken();
        entity.setAccessTokenValue(accessToken.getTokenValue());
        entity.setAccessTokenIssuedAt(accessToken.getIssuedAt());
        entity.setAccessTokenExpiresAt(accessToken.getExpiresAt());
        entity.setAccessTokenMetadata(writeMap(token.getMetadata()));
        if (accessToken.getTokenType() != null) {
            entity.setAccessTokenType(accessToken.getTokenType().getValue());
        }
        if (!CollectionUtils.isEmpty(accessToken.getScopes())) {
            entity.setAccessTokenScopes(StringUtils.collectionToDelimitedString(accessToken.getScopes(), ","));
        }
    }

    private void writeOidcIdToken(OAuth2AuthorizationEntity entity,
                                  OAuth2Authorization.Token<OidcIdToken> token) {
        if (token == null) {
            return;
        }
        OidcIdToken idToken = token.getToken();
        entity.setOidcIdTokenValue(idToken.getTokenValue());
        entity.setOidcIdTokenIssuedAt(idToken.getIssuedAt());
        entity.setOidcIdTokenExpiresAt(idToken.getExpiresAt());
        entity.setOidcIdTokenMetadata(writeMap(token.getMetadata()));
    }

    private void writeRefreshToken(OAuth2AuthorizationEntity entity,
                                   OAuth2Authorization.Token<OAuth2RefreshToken> token) {
        if (token == null) {
            return;
        }
        OAuth2RefreshToken refreshToken = token.getToken();
        entity.setRefreshTokenValue(refreshToken.getTokenValue());
        entity.setRefreshTokenIssuedAt(refreshToken.getIssuedAt());
        entity.setRefreshTokenExpiresAt(refreshToken.getExpiresAt());
        entity.setRefreshTokenMetadata(writeMap(token.getMetadata()));
    }

    private void readAuthorizationCode(OAuth2AuthorizationEntity entity,
                                       OAuth2Authorization.Builder builder) {
        if (!StringUtils.hasText(entity.getAuthorizationCodeValue())) {
            return;
        }
        OAuth2AuthorizationCode authorizationCode = new OAuth2AuthorizationCode(
                entity.getAuthorizationCodeValue(),
                entity.getAuthorizationCodeIssuedAt(),
                entity.getAuthorizationCodeExpiresAt());
        Map<String, Object> metadata = parseMap(entity.getAuthorizationCodeMetadata());
        builder.token(authorizationCode, m -> m.putAll(metadata));
    }

    private void readAccessToken(OAuth2AuthorizationEntity entity,
                                 OAuth2Authorization.Builder builder) {
        if (!StringUtils.hasText(entity.getAccessTokenValue())) {
            return;
        }
        OAuth2AccessToken.TokenType tokenType = OAuth2AccessToken.TokenType.BEARER;
        if (entity.getAccessTokenType() != null) {
            if (OAuth2AccessToken.TokenType.DPOP.getValue().equalsIgnoreCase(entity.getAccessTokenType())) {
                tokenType = OAuth2AccessToken.TokenType.DPOP;
            }
        }
        Set<String> scopes = Collections.emptySet();
        if (entity.getAccessTokenScopes() != null) {
            scopes = StringUtils.commaDelimitedListToSet(entity.getAccessTokenScopes());
        }
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                tokenType, entity.getAccessTokenValue(),
                entity.getAccessTokenIssuedAt(), entity.getAccessTokenExpiresAt(), scopes);
        Map<String, Object> metadata = parseMap(entity.getAccessTokenMetadata());
        builder.token(accessToken, m -> m.putAll(metadata));
    }

    @SuppressWarnings("unchecked")
    private void readOidcIdToken(OAuth2AuthorizationEntity entity,
                                 OAuth2Authorization.Builder builder) {
        if (!StringUtils.hasText(entity.getOidcIdTokenValue())) {
            return;
        }
        Map<String, Object> metadata = parseMap(entity.getOidcIdTokenMetadata());
        Map<String, Object> claims = (Map<String, Object>) metadata.get(
                OAuth2Authorization.Token.CLAIMS_METADATA_NAME);
        OidcIdToken idToken = new OidcIdToken(
                entity.getOidcIdTokenValue(),
                entity.getOidcIdTokenIssuedAt(),
                entity.getOidcIdTokenExpiresAt(),
                claims != null ? claims : Collections.emptyMap());
        builder.token(idToken, m -> m.putAll(metadata));
    }

    private void readRefreshToken(OAuth2AuthorizationEntity entity,
                                  OAuth2Authorization.Builder builder) {
        if (!StringUtils.hasText(entity.getRefreshTokenValue())) {
            return;
        }
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                entity.getRefreshTokenValue(),
                entity.getRefreshTokenIssuedAt(),
                entity.getRefreshTokenExpiresAt());
        Map<String, Object> metadata = parseMap(entity.getRefreshTokenMetadata());
        builder.token(refreshToken, m -> m.putAll(metadata));
    }

    private void readUserCode(OAuth2AuthorizationEntity entity,
                              OAuth2Authorization.Builder builder) {
        if (!StringUtils.hasText(entity.getUserCodeValue())) {
            return;
        }
        OAuth2UserCode userCode = new OAuth2UserCode(
                entity.getUserCodeValue(),
                entity.getUserCodeIssuedAt(),
                entity.getUserCodeExpiresAt());
        Map<String, Object> metadata = parseMap(entity.getUserCodeMetadata());
        builder.token(userCode, m -> m.putAll(metadata));
    }

    private void readDeviceCode(OAuth2AuthorizationEntity entity,
                                OAuth2Authorization.Builder builder) {
        if (!StringUtils.hasText(entity.getDeviceCodeValue())) {
            return;
        }
        OAuth2DeviceCode deviceCode = new OAuth2DeviceCode(
                entity.getDeviceCodeValue(),
                entity.getDeviceCodeIssuedAt(),
                entity.getDeviceCodeExpiresAt());
        Map<String, Object> metadata = parseMap(entity.getDeviceCodeMetadata());
        builder.token(deviceCode, m -> m.putAll(metadata));
    }

    private String writeMap(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private Map<String, Object> parseMap(String data) {
        if (data == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
}
