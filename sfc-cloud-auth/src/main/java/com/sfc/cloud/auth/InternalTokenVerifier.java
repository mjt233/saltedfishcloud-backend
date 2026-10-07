package com.sfc.cloud.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 委托令牌验签器（微服务侧）。
 * <p>
 * 用从 JWKS 取到的公钥做 <b>RS256 离线验签</b>：<b>零回调、零共享秘密</b>，
 * 不持有任何密钥材料、不签发、也不解析用户会话 JWT。
 * </p>
 *
 * <h3>强制校验项</h3>
 * <ul>
 *     <li>签名（RS256；密钥类型与算法不匹配的令牌会被 jjwt 直接拒绝）</li>
 *     <li>{@code iss} 必须等于配置的签发方（默认 {@code sfc-core}）</li>
 *     <li>{@code exp} 必须未过期（由 jjwt 自动校验）</li>
 *     <li><b>{@code aud} 必须等于本服务名</b>——与 core 侧"<code>aud ∈ 已注册服务名集合</code>"
 *     的规则刻意不同，受众绑定要防的正是微服务之间的横向重放</li>
 *     <li>{@code sub} 必须存在且可解析为用户 id</li>
 * </ul>
 * <p>
 * {@code scope} <b>不做</b>"必须有某个 scope"的判断：具体操作需要哪个作用域由
 * {@link RequireInternalToken} 在端点上声明，由 {@link RequireInternalTokenInterceptor} 强制。
 * </p>
 */
@Slf4j
public class InternalTokenVerifier {

    private final ServiceAuthClientProperties properties;
    private final JwksProvider jwksProvider;
    private final ObjectMapper headerMapper = new ObjectMapper();

    /**
     * 构造验签器。
     *
     * @param properties   微服务侧验签配置
     * @param jwksProvider 公钥提供者
     */
    public InternalTokenVerifier(ServiceAuthClientProperties properties, JwksProvider jwksProvider) {
        this.properties = properties;
        this.jwksProvider = jwksProvider;
    }

    /**
     * 校验一枚委托令牌并提取其中的主体信息。
     *
     * @param token 紧凑序列化的 JWT 字符串
     * @return 校验通过的令牌载荷
     * @throws InternalTokenException 格式非法 / 未知 kid / 验签失败 / 已过期 / iss 不符 / aud 不符 / 缺少 sub 时抛出
     */
    public InternalToken verify(String token) {
        if (!StringUtils.hasText(token)) {
            throw new InternalTokenException("委托令牌为空");
        }

        String kid = readKeyId(token);
        PublicKey publicKey = jwksProvider.resolveKey(kid);

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InternalTokenException("委托令牌验签或 claim 校验失败: " + e.getMessage(), e);
        }

        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(properties.getServiceName())) {
            throw new InternalTokenException("委托令牌的 aud 与本服务名不匹配：本服务名为 "
                    + properties.getServiceName() + "，令牌 aud=" + audience);
        }

        String subject = claims.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new InternalTokenException("委托令牌缺少 sub claim");
        }
        long uid;
        try {
            uid = Long.parseLong(subject.trim());
        } catch (NumberFormatException e) {
            throw new InternalTokenException("委托令牌的 sub 不是合法的用户 id: " + subject, e);
        }

        return new InternalToken(uid, parseScopes(claims.get("scope", String.class)),
                properties.getServiceName(), claims.getId());
    }

    /**
     * 从令牌头部读出 {@code kid}。
     * <p>
     * 先解头部再验签，是为了把"未知 {@code kid} ⇒ 回源一次 ⇒ 再判定失败"这条轮换契约
     * 显式地放在本类里，而不是藏在 jjwt 的 {@code keyLocator} 回调内部。
     * </p>
     *
     * @param token 紧凑序列化的 JWT
     * @return 头部中的 {@code kid}
     * @throws InternalTokenException 头部无法解析或缺少 kid 时抛出
     */
    private String readKeyId(String token) {
        int firstDot = token.indexOf('.');
        if (firstDot <= 0) {
            throw new InternalTokenException("委托令牌格式非法：缺少 JWT 头部");
        }
        JsonNode header;
        try {
            header = headerMapper.readTree(Base64.getUrlDecoder().decode(token.substring(0, firstDot)));
        } catch (Exception e) {
            throw new InternalTokenException("无法解析委托令牌头部", e);
        }
        JsonNode kid = header == null ? null : header.get("kid");
        if (kid == null || kid.isNull() || !StringUtils.hasText(kid.asText())) {
            throw new InternalTokenException("委托令牌头部缺少 kid");
        }
        return kid.asText();
    }

    /**
     * 解析空格分隔的 {@code scope} claim。
     *
     * @param raw scope 原文，可为 {@code null}
     * @return 去重后的作用域集合，无 scope 时返回空集合
     */
    private static Set<String> parseScopes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptySet();
        }
        Set<String> scopes = new LinkedHashSet<>();
        Arrays.stream(raw.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .forEach(scopes::add);
        return scopes;
    }
}
