package com.xiaotao.saltedfishcloud.service.auth;

import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;

/**
 * 委托令牌签发器——<b>全链路唯一的签发方</b>。
 * <p>
 * 使用自持的 RSA 私钥以 <b>RS256</b> 签名，<b>不注入任何 OIDC / OAuth bean</b>，
 * 与 {@code OidcAuthorizationServerConfig} 的密钥体系完全无关。
 * </p>
 *
 * <h3>令牌形态</h3>
 * <table border="1">
 *     <caption>Claims</caption>
 *     <tr><th>Claim</th><th>取值</th></tr>
 *     <tr><td>{@code iss}</td><td>固定为 {@value #ISSUER}</td></tr>
 *     <tr><td>{@code aud}</td><td>签发时即固定为"目标微服务名"，<b>透传时不改写</b></td></tr>
 *     <tr><td>{@code sub}</td><td>用户 uid 的字符串形式——uid <b>只存在于令牌里</b></td></tr>
 *     <tr><td>{@code scope}</td><td>空格分隔的 {@code internal.*} 作用域</td></tr>
 *     <tr><td>{@code iat} / {@code exp}</td><td>签发时间与过期时间，控制面 TTL ≤ 60 秒</td></tr>
 *     <tr><td>{@code jti}</td><td>随机串，仅用于日志关联，<b>不用于去重</b>（无状态设计）</td></tr>
 * </table>
 *
 * <h3>刻意不包含 {@code roles}</h3>
 * <p>
 * 角色由校验方按 {@code sub} 实时装载用户获得<b>最新</b>值；若写进令牌，
 * 用户被降权后仍会在令牌有效期内继续以旧角色生效。
 * </p>
 *
 * <h3>每请求现签，不复用不缓存</h3>
 * <p>
 * 复用需要缓存键（uid ＋ aud ＋ scope）与失效逻辑，收益只是省下一次签名运算（量级为微秒），
 * 用复杂度换微秒不划算；而"每请求现签"正是 TTL 可以压到 60 秒以内、
 * 因而<b>不需要撤销列表</b>的原因。
 * </p>
 *
 * @see DelegationTokenVerifier
 * @see ServiceAuthProperties
 */
@Component
@RequiredArgsConstructor
public class DelegationTokenIssuer {

    /**
     * 令牌签发方标识，同时是校验方要求必须匹配的 {@code iss}。
     */
    public static final String ISSUER = "sfc-core";

    private final ServiceAuthProperties properties;

    /**
     * 按控制面 TTL 签发一枚委托令牌。
     *
     * @param uid      用户 id，写入 {@code sub}；不得为 {@code null}
     * @param audience 目标微服务名，写入 {@code aud}
     * @param scope    所需作用域，取值见 {@link com.xiaotao.saltedfishcloud.constant.ServiceAuthScopes}；可为空
     * @return 紧凑序列化后的 JWT 字符串
     * @throws IllegalStateException 签名密钥不可用时抛出（配置错误显式暴露，不静默降级）
     */
    public String issue(Long uid, String audience, String scope) {
        if (uid == null) {
            throw new IllegalArgumentException("委托令牌的 sub（用户 uid）不能为空");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("委托令牌的 aud（目标服务名）不能为空：受众绑定是防止令牌被用于其它服务的前提");
        }

        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(properties.getTokenTtlSeconds());

        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(audience).and()
                .subject(String.valueOf(uid))
                .claim("scope", scope)
                .id(SecureUtils.getUUID())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .header().keyId(properties.requireKeyId()).and()
                .signWith(properties.requirePrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 按控制面 TTL 签发一枚委托令牌，作用域由集合拼成空格分隔的字符串。
     *
     * @param uid      用户 id，写入 {@code sub}
     * @param audience 目标微服务名，写入 {@code aud}
     * @param scopes   作用域集合；为空时 {@code scope} claim 为空
     * @return 紧凑序列化后的 JWT 字符串
     */
    public String issue(Long uid, String audience, Collection<String> scopes) {
        String scope = (scopes == null || scopes.isEmpty()) ? null : String.join(" ", scopes);
        return issue(uid, audience, scope);
    }
}
