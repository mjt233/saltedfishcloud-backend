package com.xiaotao.saltedfishcloud.service.registry.local;

import com.sfc.common.exception.SfcJsonException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * local 注册中心外部接口的密码校验。
 * <p>
 * 密码留空时拒绝所有外部请求（fail-closed）；比较使用常量时间算法，避免时序侧信道。
 */
public class LocalRegistryAuthService {
    /** 本地注册中心服务端配置 */
    private final SfcLocalRegistryServerProperties properties;

    /**
     * 创建密码校验服务。
     *
     * @param properties 本地注册中心服务端配置
     */
    public LocalRegistryAuthService(SfcLocalRegistryServerProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验请求携带的密码。
     *
     * @param providedPassword 请求头中的密码
     * @throws SfcJsonException 未配置密码或密码不匹配时抛出 403
     */
    public void verify(String providedPassword) {
        String expectedPassword = expectedPassword();
        if (expectedPassword == null || expectedPassword.isBlank()) {
            throw new SfcJsonException(403,
                    "本地注册中心未配置密码（spring.cloud.sfc-local.registry.password），外部注册接口已禁用");
        }
        if (providedPassword == null || providedPassword.isBlank()
                || !MessageDigest.isEqual(
                        expectedPassword.getBytes(StandardCharsets.UTF_8),
                        providedPassword.getBytes(StandardCharsets.UTF_8))) {
            throw new SfcJsonException(403, "注册中心密码错误");
        }
    }

    /**
     * 获取配置的密码。
     *
     * @return 配置的密码，可能为 null
     */
    private String expectedPassword() {
        return properties == null ? null : properties.getPassword();
    }
}
