package com.sfc.gateway.auth;

import com.sfc.gateway.config.SfcGatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 用户会话凭证的<b>本地验签</b>：网关独立完成"这个 token 是不是 core 签发的、有没有过期"。
 *
 * <h3>与 core 的口径逐条对齐</h3>
 * <table border="1">
 *     <caption>对齐点</caption>
 *     <tr><th>关注点</th><th>core（{@code JwtUtils}）</th><th>本类</th></tr>
 *     <tr><td>密钥派生</td><td>{@code Keys.hmacShaKeyFor(secret.getBytes(UTF_8))}</td>
 *         <td>同一 API：算法随密钥长度自适应（HS256 / HS384 / HS512），<b>不写死算法</b></td></tr>
 *     <tr><td>验签与过期</td><td>{@code Jwts.parser().verifyWith(key).parseSignedClaims(token)}</td>
 *         <td>同上；{@code exp} 缺失视为永久有效（core 的 {@code generateToken(expr<=0)} 语义）</td></tr>
 *     <tr><td>身份字段</td><td>payload 的 {@code data} 是 UserPrincipal 的 JSON 字符串</td>
 *         <td>读 {@code data} 里的 {@code id}（只取 uid，不反序列化咸鱼云类型）</td></tr>
 * </table>
 *
 * <h3>刻意不做的两件事</h3>
 * <ol>
 *     <li><b>不查 Redis 会话白名单</b>（core 的 {@code TokenService.isTokenValid}）。因此登出 / 改密 / 踢下线后，
 *     旧 token 在网关侧<b>仍然算已认证</b>；完整校验由拿到 token 原值的下游完成——已记账为技术债务
 *     （见 {@code docs/plan/todo.md}）。</li>
 *     <li><b>不感知密钥的在线变更</b>：core 支持在线修改 {@code sys.safe.token}，网关只读启动时的环境变量，
 *     密钥变更后必须重启网关并重新注入。</li>
 * </ol>
 *
 * <p>
 * ⚠️ 密钥缺失或短于 32 字节时<b>启动即失败</b>（构造器抛异常）：这是有意的 fail-fast——否则网关会正常启动，
 * 而所有 {@code /api/ext/**} 请求都以 403/7005 失败，排查方向会被严重误导。
 * </p>
 */
@Slf4j
@Component
public class GatewayTokenVerifier {

    /** JWT payload 中承载主体的字段名（与 core 的 {@code JwtUtils.generateToken} 一致） */
    private static final String CLAIM_DATA = "data";

    /** 主体 JSON 中的用户 id 字段（与 core 的 {@code UserPrincipal#id} 一致） */
    private static final String FIELD_ID = "id";

    /** jjwt 0.12+ 对 HMAC-SHA 密钥的最小长度要求：256 bit */
    private static final int MIN_SECRET_BYTES = 32;

    /** 验签密钥（由环境变量注入的密钥派生，算法随长度自适应） */
    private final SecretKey key;

    /** JSON 解析器（仅用于读取 {@code data} 里的 uid，不反序列化任何咸鱼云类型） */
    private final ObjectMapper objectMapper;

    /**
     * 构造器：读取密钥并完成启动期校验。
     *
     * @param properties   网关配置（提供密钥）
     * @param objectMapper Boot 4 提供的 JSON 序列化器
     * @throws IllegalStateException 密钥缺失或长度不足
     */
    public GatewayTokenVerifier(SfcGatewayProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String secret = properties.getJwtSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("缺少 JWT 密钥：请通过环境变量 " + SfcGatewayProperties.JWT_SECRET_ENV
                    + " 注入 core 的会话密钥（core 侧配置项 sys.safe.token 的值）。"
                    + "网关不再向 core 询问会话，没有密钥就无法验证任何用户凭证，因此拒绝启动。");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT 密钥长度不足：环境变量 " + SfcGatewayProperties.JWT_SECRET_ENV
                    + " 的 UTF-8 字节数必须不少于 " + MIN_SECRET_BYTES + "（当前 " + secretBytes.length
                    + " 字节）。请确认注入的是 core 的完整密钥原值。");
        }
        // 与 core 用同一个 API：不指定算法，由密钥长度决定（HS256 / HS384 / HS512），保证两侧判定完全一致
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 验证凭证并取出用户 id。
     *
     * @param token 凭证原值（{@code Token} 头 / {@code ?Token=} / {@code token} Cookie 三者之一）
     * @return 用户 id；验签失败、已过期、载荷不完整时返回 {@code null}
     */
    public Long verify(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return readUid(claims.get(CLAIM_DATA));
        } catch (ExpiredJwtException e) {
            log.debug("[凭证] 已过期：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            // 签名不符、结构损坏、载荷非法等一律视为"无效凭证"：对外语义与 core 的
            // JwtAuthenticationFilter 一致（它同样吞掉异常并继续以匿名身份放行，最终由鉴权层给 403）
            log.debug("[凭证] 验签失败：{}", e.toString());
            return null;
        }
    }

    /**
     * 从 {@code data} 声明里取出 uid。
     *
     * <p>
     * {@code data} 是 core 序列化出的 UserPrincipal JSON 字符串（含 {@code @class} 等字段），
     * 这里只读 {@code id}：用 Map 承接而不是反序列化成 POJO —— 网关零咸鱼云依赖，
     * 引用不到 {@code UserPrincipal}，也不应因为它多加一个字段就解析失败。
     * </p>
     *
     * @param dataClaim {@code data} 声明的值
     * @return 用户 id；缺失或格式非法时返回 {@code null}
     */
    private Long readUid(Object dataClaim) {
        if (!(dataClaim instanceof String json) || json.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(json, Map.class);
            Object id = payload.get(FIELD_ID);
            if (id instanceof Number number) {
                return number.longValue();
            }
            if (id instanceof String text && !text.isBlank()) {
                return Long.parseLong(text.trim());
            }
            return null;
        } catch (Exception e) {
            log.debug("[凭证] 载荷无法解析出 uid：{}", e.toString());
            return null;
        }
    }
}
