package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.xiaotao.saltedfishcloud.config.oidc.OidcServerProperty;
import org.springframework.stereotype.Service;

/**
 * OIDC JWK 服务，负责生成或加载用于 JWT 令牌签名的 RSA 密钥集。
 * <p>
 * 当前实现在每次调用 {@link #loadOrCreate(OidcServerProperty)} 时生成新的 2048 位 RSA 密钥对。
 * 后续任务可扩展为从文件（{@link OidcServerProperty.Jwk#getKeyStorePath()}）持久化加载密钥，
 * 以实现服务重启后 JWK 的一致性。
 * </p>
 */
@Service
public class OidcJwkService {

    /**
     * 根据配置生成 RSA JWK 密钥集。
     * <p>
     * 生成一个 2048 位的 RSA 密钥对，keyId 取自 {@link OidcServerProperty.Jwk#getKeyId()}。
     * </p>
     *
     * @param property OIDC 服务端配置属性
     * @return 包含 RSA 私钥的 {@link JWKSet}
     * @throws IllegalStateException 如果 RSA 密钥生成失败
     */
    public JWKSet loadOrCreate(OidcServerProperty property) {
        try {
            RSAKey rsaKey = new RSAKeyGenerator(2048)
                    .keyID(property.getJwk().getKeyId())
                    .generate();
            return new JWKSet(rsaKey);
        } catch (JOSEException e) {
            throw new IllegalStateException("OIDC JWK RSA 密钥生成失败", e);
        }
    }
}
