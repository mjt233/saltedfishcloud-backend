package com.xiaotao.saltedfishcloud.config.oidc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OidcServerProperty#validate()} 快速失败逻辑的单元测试。
 * <p>
 * 不依赖 Spring 上下文，直接构造 {@link OidcServerProperty} 实例进行验证，
 * 覆盖"已启用但 issuer 为空"和"未启用时跳过校验"两个核心分支。
 * </p>
 */
class OidcServerPropertyValidationTest {

    /**
     * 验证当 OIDC 已启用且 issuer 为空字符串时，{@link OidcServerProperty#validate()} 抛出异常（快速失败）。
     */
    @Test
    void shouldThrowWhenEnabledWithBlankIssuer() {
        OidcServerProperty prop = new OidcServerProperty();
        prop.setEnabled(true);
        prop.setIssuer("");
        assertThrows(IllegalStateException.class, prop::validate,
                "sys.oidc.enabled=true 时 issuer 为空字符串应抛出 IllegalStateException");
    }

    /**
     * 验证当 OIDC 已启用且 issuer 为 null 时，{@link OidcServerProperty#validate()} 抛出异常。
     */
    @Test
    void shouldThrowWhenEnabledWithNullIssuer() {
        OidcServerProperty prop = new OidcServerProperty();
        prop.setEnabled(true);
        prop.setIssuer(null);
        assertThrows(IllegalStateException.class, prop::validate,
                "sys.oidc.enabled=true 时 issuer 为 null 应抛出 IllegalStateException");
    }

    /**
     * 验证当 OIDC 未启用时，即使 issuer 为 null，{@link OidcServerProperty#validate()} 也不抛出异常。
     */
    @Test
    void shouldNotThrowWhenDisabledEvenIfIssuerIsNull() {
        OidcServerProperty prop = new OidcServerProperty();
        prop.setEnabled(false);
        prop.setIssuer(null);
        assertDoesNotThrow(prop::validate,
                "sys.oidc.enabled=false 时即使 issuer 为 null 也不应抛出异常");
    }

    /**
     * 验证当 OIDC 已启用且 issuer 有效时，{@link OidcServerProperty#validate()} 正常通过。
     */
    @Test
    void shouldNotThrowWhenEnabledWithValidIssuer() {
        OidcServerProperty prop = new OidcServerProperty();
        prop.setEnabled(true);
        prop.setIssuer("https://cloud.example.com");
        assertDoesNotThrow(prop::validate,
                "sys.oidc.enabled=true 且 issuer 有效时不应抛出异常");
    }
}
