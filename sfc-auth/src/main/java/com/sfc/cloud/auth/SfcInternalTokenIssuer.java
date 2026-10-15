package com.sfc.cloud.auth;

import io.jsonwebtoken.Jwts;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 内部调用凭证签发器：用共享密钥为<b>每一次调用</b>签发一个新的 HS256 JWT。
 *
 * <p>JWT 声明约定：</p>
 * <ul>
 *   <li>{@code iss}：本服务名（{@code sfc.internal.service-name} 或 {@code spring.application.name}）</li>
 *   <li>{@code sub}：固定 {@link SfcInternalAuthConstants#JWT_SUBJECT}，与用户会话凭证区分</li>
 *   <li>{@code jti}：16 字节随机值，仅作<b>追踪标识</b>（服务端日志据此关联同一凭证）</li>
 *   <li>{@code iat} / {@code exp}：签发时间与到期时间（{@code sfc.internal.token-ttl}，默认 5 分钟）</li>
 * </ul>
 *
 * <p><b>每请求签发新凭证</b>：本 SDK 不做防重放（见 {@link SfcInternalTokenVerifier}），
 * 因此客户端既可以逐请求签发，也可以按需要缓存复用；逐请求签发的代价只是一次 HS256，可忽略。</p>
 *
 * <p>密钥只参与本地签名，<b>永不出现在请求里</b>；日志中也不得打印凭证与密钥。</p>
 */
public class SfcInternalTokenIssuer {

    /** jti 随机源 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** jti 字节长度 */
    private static final int JTI_BYTES = 16;

    /** 共享密钥（可能为"未配置"状态） */
    private final SfcInternalAuthKey authKey;

    /** 写入 {@code iss} 的本服务名 */
    private final String serviceName;

    /** 凭证有效期 */
    private final Duration tokenTtl;

    /**
     * 构造签发器。
     *
     * @param authKey     共享密钥
     * @param serviceName 本服务名，写入 {@code iss}
     * @param tokenTtl    凭证有效期
     */
    public SfcInternalTokenIssuer(SfcInternalAuthKey authKey, String serviceName, Duration tokenTtl) {
        this.authKey = authKey;
        this.serviceName = serviceName;
        this.tokenTtl = tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()
                ? Duration.ofMinutes(5)
                : tokenTtl;
    }

    /**
     * 签发一个新的内部调用凭证。
     *
     * @return JWT 紧凑序列化文本（放入 {@link SfcInternalAuthConstants.Header#INTERNAL_TOKEN} 请求头）
     * @throws IllegalStateException 未配置共享密钥时抛出——宁可本地明确失败，也不要发出必然被拒绝空的请求
     */
    public String issue() {
        if (!authKey.isConfigured()) {
            throw new IllegalStateException("未配置 sfc.internal.key（环境变量 SFC_INTERNAL_KEY），"
                    + "无法生成内部接口调用凭证");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(serviceName)
                .subject(SfcInternalAuthConstants.JWT_SUBJECT)
                .id(randomId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(authKey.requireSecretKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 生成 jti。
     *
     * @return 16 字节随机值的 base64url 文本（无填充）
     */
    private String randomId() {
        byte[] bytes = new byte[JTI_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
