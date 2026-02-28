package com.xiaotao.saltedfishcloud.service.third.handler.google;

import com.xiaotao.saltedfishcloud.annotations.ConfigKeyNameStrategy;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;

@Data
@ConfigPropertyEntity(defaultKeyNameStrategy = ConfigKeyNameStrategy.CAMEL_CASE)
public class GooglePlatformProperty {
    @ConfigProperty
    private String clientId;

    @ConfigProperty
    private String clientSecret;

    @ConfigProperty(describe = "Google认证完成后，重定向到的URL。一般设置到 https://咸鱼云服务/api/oauth/callback/google。该值需要与Google Cloud Console Clients page中配置的 OAuth 2.0 客户端的某个已获授权的重定向 URI 字符串完全一致")
    private String redirectUrl;
}
