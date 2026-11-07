package com.sfc.cloud.auth;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 已解析的服务间共享密钥：把"配置项文本"与"jjwt 需要的 {@link SecretKey}"分开，并集中表达
 * <b>未配置</b>这一 fail-closed 状态。
 *
 * <p>未配置时 {@link #isConfigured()} 为 false，签发与校验都会明确失败，而不是退化为"放行"。</p>
 */
public final class SfcInternalAuthKey {

    /** 未配置密钥的单例（无密钥） */
    private static final SfcInternalAuthKey UNCONFIGURED = new SfcInternalAuthKey(null);

    /** HS256 签名密钥；未配置时为 null */
    private final SecretKey secretKey;

    /**
     * 内部构造：请使用 {@link #unconfigured()} 或 {@link #fromKeyText(String)}。
     *
     * @param secretKey 签名密钥，可为 null 表示未配置
     */
    private SfcInternalAuthKey(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 构造"未配置密钥"状态。
     *
     * @return 未配置状态的实例
     */
    public static SfcInternalAuthKey unconfigured() {
        return UNCONFIGURED;
    }

    /**
     * 由配置项文本构造密钥。
     *
     * @param keyText 配置的共享密钥；空白表示未配置
     * @return 已配置或未配置的密钥实例
     * @throws IllegalStateException 密钥非空但长度不足 {@link SfcInternalAuthConstants#MIN_KEY_LENGTH} 时抛出（启动期即失败）
     */
    public static SfcInternalAuthKey fromKeyText(String keyText) {
        if (keyText == null || keyText.isBlank()) {
            return UNCONFIGURED;
        }
        if (keyText.length() < SfcInternalAuthConstants.MIN_KEY_LENGTH) {
            throw new IllegalStateException("sfc.internal.key 长度不足 " + SfcInternalAuthConstants.MIN_KEY_LENGTH
                    + " 个字符（当前 " + keyText.length() + "）。HS256 要求至少 256 bit 密钥，"
                    + "请用 `openssl rand -hex 16` 之类的方式生成后重新注入");
        }
        return new SfcInternalAuthKey(Keys.hmacShaKeyFor(keyText.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 是否已配置密钥。
     *
     * @return true 表示可以签发与校验凭证
     */
    public boolean isConfigured() {
        return secretKey != null;
    }

    /**
     * 获取签名密钥。
     *
     * @return HS256 签名密钥
     * @throws IllegalStateException 未配置密钥时抛出
     */
    public SecretKey requireSecretKey() {
        if (secretKey == null) {
            throw new IllegalStateException("未配置 sfc.internal.key（环境变量 SFC_INTERNAL_KEY）");
        }
        return Objects.requireNonNull(secretKey);
    }
}
