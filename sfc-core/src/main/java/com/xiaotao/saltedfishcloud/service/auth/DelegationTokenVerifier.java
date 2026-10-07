package com.xiaotao.saltedfishcloud.service.auth;

import com.sfc.cloud.registry.ServiceDiscovery;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 委托令牌校验器——core 侧的自持验签实现。
 * <p>
 * <b>不注入任何 OIDC bean</b>：直接用 jjwt 持有公钥离线验签，
 * 不经过 OIDC 的 {@code JwtDecoder}，也不依赖 {@code /oauth2/jwks} 端点。
 * </p>
 *
 * <h3>校验项</h3>
 * <ul>
 *     <li>签名（RS256，用自持公钥。密钥类型与算法不匹配的令牌会被 jjwt 直接拒绝，
 *     因此"用公钥当 HMAC 密钥"的算法混淆攻击不成立）</li>
 *     <li>{@code iss} 必须等于 {@value DelegationTokenIssuer#ISSUER}</li>
 *     <li>{@code exp} 必须未过期（由 jjwt 自动校验）</li>
 *     <li>{@code aud} 必须属于<b>已注册服务名集合 ∪ {@code allowed-audiences} 白名单</b></li>
 *     <li>{@code sub} 必须存在且可解析为用户 uid</li>
 * </ul>
 *
 * <h3>⚠️ 最容易写错的一处：{@code aud} 在两个校验方的规则不一样</h3>
 * <table border="1">
 *     <caption>两侧规则对比</caption>
 *     <tr><th>校验方</th><th>规则</th><th>为什么</th></tr>
 *     <tr><td>微服务</td><td>{@code aud == 自己的服务名}</td>
 *         <td>这正是"受众绑定"要防的事：给 A 的令牌不能被 B 接受，阻断微服务之间的横向重放</td></tr>
 *     <tr><td><b>core 的 {@code /api/internal/**}（本类）</b></td>
 *         <td><b>{@code aud ∈ 已注册服务名集合}</b></td>
 *         <td>令牌本就是 core 签发的，它用 {@code iss} 认领签发权即可；{@code aud} 在这里表达的是
 *         "这枚令牌被签发给链路上的哪个微服务"。<b>若写成 {@code aud == sfc-core}，
 *         "core → 微服务 → core"的透传链路会当场断掉</b>，且症状是"代理层通了、回调一直 401"这种很难定位的形态</td></tr>
 * </table>
 * <p>
 * <b>请勿把这一条"顺手修正"成 {@code aud == sfc-core}。</b>
 * </p>
 *
 * <h3>作用域在这里不强制</h3>
 * <p>
 * 本类只把 {@code scope} claim 解析出来交给调用方转成 {@code SCOPE_*} authority，
 * <b>不做</b>"必须有某个 scope"的判断——具体操作需要哪个 scope 由端点的
 * {@link com.xiaotao.saltedfishcloud.annotations.RequireScope} 声明，
 * 由既有的 {@code RequireScopeAspect} 抛出 {@code AccessDeniedException}（403）。
 * 这与设计文档 §3.3 的失败语义对齐。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelegationTokenVerifier {

    /**
     * 已注册服务名的本地缓存时长（毫秒）。
     * <p>
     * 为什么需要缓存：{@code registry-provider=nacos} 时 {@link ServiceDiscovery#listServiceNames()}
     * 是一次<b>服务端查询</b>。若每个内部 API 请求都去问一次注册中心，
     * 就等于给内部调用链路额外加了一跳 RPC，还把注册中心的可用性变成了鉴权的前置依赖。
     * 缓存窗口内用快照判定即可——令牌的 {@code aud} 是签发时确定的目标服务名，
     * 对一份 10 秒内的服务名列表做成员判定是充分的。
     * </p>
     */
    private static final long SERVICE_NAME_CACHE_MILLIS = 10_000L;

    /** 配置属性，提供公钥与 {@code aud} 追加白名单 */
    private final ServiceAuthProperties properties;

    /**
     * 服务发现 SPI。
     * <p>
     * 用 {@link ObjectProvider} 延迟解析：{@code registry-provider} 为 {@code nacos} 时，
     * 实现 Bean 由插件（{@code AutoConfiguration.imports}）注册，注册时机晚于 {@code @Component} 扫描，
     * 在构造期直接注入会引入不必要的启动顺序耦合；同时也不存在"没有任何实现"的歧义——
     * 此时退化为"只用白名单判定"，属于 fail-closed 方向。
     * </p>
     */
    private final ObjectProvider<ServiceDiscovery> discoveryProvider;

    /** 已注册服务名快照 */
    private volatile List<String> serviceNameSnapshot = Collections.emptyList();

    /** 服务名快照的过期时间戳（毫秒） */
    private volatile long serviceNameSnapshotExpireAt = 0L;

    /**
     * 校验一枚委托令牌并提取其中的主体信息。
     *
     * @param token 紧凑序列化的 JWT 字符串
     * @return 校验通过后的令牌载荷
     * @throws InvalidDelegationTokenException 验签失败 / 已过期 / {@code iss} 不符 / {@code aud} 不符 / 缺少 {@code sub}
     * @throws IllegalStateException           公钥不可用（配置错误，调用方应按 500 处理而非 401）
     */
    public VerifiedToken verify(String token) {
        // 密钥不可用时 requirePublicKey 会抛 IllegalStateException——刻意让它冒到调用方，
        // 使"配置错误"显式暴露为 500，而不是静默退化成 401
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(properties.requirePublicKey())
                    .requireIssuer(DelegationTokenIssuer.ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidDelegationTokenException("委托令牌验签或 claim 校验失败: " + e.getMessage(), e);
        }

        String audience = resolveAudience(claims);
        if (!isAudienceAllowed(audience)) {
            throw new InvalidDelegationTokenException("委托令牌的 aud 不在允许的服务名集合内: " + audience);
        }

        String subject = claims.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new InvalidDelegationTokenException("委托令牌缺少 sub claim");
        }

        long uid;
        try {
            uid = Long.parseLong(subject.trim());
        } catch (NumberFormatException e) {
            throw new InvalidDelegationTokenException("委托令牌的 sub 不是合法的用户 id: " + subject, e);
        }

        return new VerifiedToken(uid, parseScopes(claims.get("scope", String.class)),
                audience, claims.getId());
    }

    /**
     * 判断目标服务名是否被允许。
     * <p>
     * 先查配置白名单（命中即返回，不触碰服务发现），再查已注册服务名快照。
     * </p>
     *
     * @param audience 令牌的 {@code aud}
     * @return 允许时返回 {@code true}
     */
    public boolean isAudienceAllowed(String audience) {
        if (!StringUtils.hasText(audience)) {
            return false;
        }
        List<String> allowed = properties.getAllowedAudiences();
        if (allowed != null && allowed.contains(audience)) {
            return true;
        }
        return registeredServiceNames().contains(audience);
    }

    /**
     * 取已注册服务名快照，超过缓存窗口时回源刷新一次。
     * <p>
     * 服务发现不可用（无实现 / 查询抛异常）时返回空列表，并同样写入缓存，
     * 避免请求风暴期间反复穿透到注册中心。
     * </p>
     *
     * @return 服务名列表，无可用来源时返回空列表
     */
    private List<String> registeredServiceNames() {
        long now = System.currentTimeMillis();
        if (now < serviceNameSnapshotExpireAt) {
            return serviceNameSnapshot;
        }
        List<String> resolved = Collections.emptyList();
        ServiceDiscovery discovery = discoveryProvider.getIfAvailable();
        if (discovery != null) {
            try {
                List<String> names = discovery.listServiceNames();
                if (names != null) {
                    resolved = List.copyOf(names);
                }
            } catch (Exception e) {
                log.warn("读取已注册服务名失败，本次 aud 判定退化为仅使用 allowed-audiences 白名单: {}", e.getMessage());
            }
        }
        serviceNameSnapshot = resolved;
        serviceNameSnapshotExpireAt = now + SERVICE_NAME_CACHE_MILLIS;
        return resolved;
    }

    /**
     * 从 claims 中取受众，缺失或为空时抛"凭证无效"。
     *
     * @param claims 令牌载荷
     * @return 目标服务名
     */
    private static String resolveAudience(Claims claims) {
        Set<String> audience = claims.getAudience();
        if (audience == null || audience.isEmpty()) {
            throw new InvalidDelegationTokenException("委托令牌缺少 aud claim：受众绑定是必校验项");
        }
        return audience.iterator().next();
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
        return Collections.unmodifiableSet(scopes);
    }

    /**
     * 校验通过的委托令牌载荷。
     *
     * @param uid      用户 id，来自 {@code sub}——uid <b>只能</b>来自这里
     * @param scopes   作用域集合，来自 {@code scope}
     * @param audience 目标服务名，来自 {@code aud}
     * @param jwtId    令牌 id，来自 {@code jti}，仅用于日志关联
     */
    public record VerifiedToken(Long uid, Set<String> scopes, String audience, String jwtId) {
    }
}
