package com.sfc.cloud.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.PrematureJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 内部调用凭证校验器：验签 + 有效期（含时钟偏移）+ 必需声明 + 可选服务名白名单。
 */
public class SfcInternalTokenVerifier {

    /** 共享密钥（可能为"未配置"状态，此时一切校验失败） */
    private final SfcInternalAuthKey authKey;

    /** 允许的时钟偏移 */
    private final Duration clockSkew;

    /** 允许的调用方服务名；空表示不校验 */
    private final List<String> allowedIssuers;

    /**
     * 构造校验器。
     *
     * @param authKey        共享密钥
     * @param clockSkew      允许的时钟偏移，null 视为 0
     * @param allowedIssuers 允许的调用方服务名，null/空表示不校验
     */
    public SfcInternalTokenVerifier(SfcInternalAuthKey authKey, Duration clockSkew, List<String> allowedIssuers) {
        this.authKey = authKey;
        this.clockSkew = clockSkew == null || clockSkew.isNegative() ? Duration.ZERO : clockSkew;
        this.allowedIssuers = allowedIssuers == null ? Collections.emptyList() : List.copyOf(allowedIssuers);
    }

    /**
     * 校验一个内部调用凭证。
     *
     * @param token 请求头带来的 JWT，可为 null
     * @return 校验结果（含失败原因），本方法不抛异常
     */
    public SfcInternalTokenVerificationResult verify(String token) {
        if (!authKey.isConfigured()) {
            return SfcInternalTokenVerificationResult.failure(
                    SfcInternalTokenVerificationResult.Failure.KEY_NOT_CONFIGURED, null, null);
        }
        if (!StringUtils.hasText(token)) {
            return SfcInternalTokenVerificationResult.failure(
                    SfcInternalTokenVerificationResult.Failure.MISSING_TOKEN, null, null);
        }

        Jws<Claims> jws;
        try {
            jws = Jwts.parser()
                    .verifyWith(authKey.requireSecretKey())
                    .clockSkewSeconds(clockSkew.getSeconds())
                    .build()
                    .parseSignedClaims(token);
        } catch (ExpiredJwtException e) {
            return failureWithClaims(SfcInternalTokenVerificationResult.Failure.EXPIRED, e.getClaims());
        } catch (PrematureJwtException e) {
            return failureWithClaims(SfcInternalTokenVerificationResult.Failure.NOT_YET_VALID, e.getClaims());
        } catch (SignatureException e) {
            return SfcInternalTokenVerificationResult.failure(
                    SfcInternalTokenVerificationResult.Failure.BAD_SIGNATURE, null, null);
        } catch (JwtException | IllegalArgumentException e) {
            return SfcInternalTokenVerificationResult.failure(
                    SfcInternalTokenVerificationResult.Failure.MALFORMED, null, null);
        }

        Claims claims = jws.getPayload();
        String issuer = claims.getIssuer();
        String jti = claims.getId();
        Date expiration = claims.getExpiration();
        if (!StringUtils.hasText(issuer) || !StringUtils.hasText(jti) || expiration == null
                || !SfcInternalAuthConstants.JWT_SUBJECT.equals(claims.getSubject())) {
            return SfcInternalTokenVerificationResult.failure(
                    SfcInternalTokenVerificationResult.Failure.MISSING_CLAIM, issuer, jti);
        }
        if (!allowedIssuers.isEmpty() && !allowedIssuers.contains(issuer)) {
            return SfcInternalTokenVerificationResult.failure(
                    SfcInternalTokenVerificationResult.Failure.ISSUER_NOT_ALLOWED, issuer, jti);
        }
        return SfcInternalTokenVerificationResult.success(issuer, jti);
    }

    /**
     * 由异常携带的声明构造失败结果（过期/未生效场景下声明仍可解析，便于日志定位是哪一个服务）。
     *
     * @param failure 失败原因
     * @param claims  异常携带的声明，可为 null
     * @return 校验结果
     */
    private SfcInternalTokenVerificationResult failureWithClaims(
            SfcInternalTokenVerificationResult.Failure failure, Claims claims) {
        return claims == null
                ? SfcInternalTokenVerificationResult.failure(failure, null, null)
                : SfcInternalTokenVerificationResult.failure(failure, claims.getIssuer(), claims.getId());
    }
}
