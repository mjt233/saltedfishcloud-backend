package com.sfc.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sfc.gateway")
public class SfcGatewayProperties {

    /**
     * 用户会话凭证的验签密钥（HS256 家族，与 core 的 {@code JwtUtils} 共用同一份密钥）。
     */
    private String jwtSecret;
}
