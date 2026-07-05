package com.xiaotao.saltedfishcloud.config.oidc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OidcServerProperty#validate()} 快速失败逻辑的单元测试。
 * <p>
 * 不依赖 Spring 上下文，直接构造 {@link OidcServerProperty} 实例进行验证。
 * </p>
 */
class OidcServerPropertyValidationTest {

    /**
     * 验证当 OIDC 已启用时，{@link OidcServerProperty#validate()} 正常通过。
     */
    @Test
    void shouldNotThrowWhenEnabled() {
        OidcServerProperty prop = new OidcServerProperty();
        prop.setEnabled(true);
        assertDoesNotThrow(prop::validate,
                "sys.oidc.enabled=true 时 validate() 不应抛出异常");
    }

    /**
     * 验证当 OIDC 未启用时，{@link OidcServerProperty#validate()} 正常通过。
     */
    @Test
    void shouldNotThrowWhenDisabled() {
        OidcServerProperty prop = new OidcServerProperty();
        prop.setEnabled(false);
        assertDoesNotThrow(prop::validate,
                "sys.oidc.enabled=false 时 validate() 不应抛出异常");
    }
}
