package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.model.config.SysSafeConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 系统安全配置自动注册
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class SysSafeConfigConfiguration {
    private final ConfigService configService;
    private final HelloService helloService;

    @Bean
    public SysSafeConfig sysSafeConfig() {
        SysSafeConfig config = new SysSafeConfig();
        configService.bindPropertyEntity(config);

        initJwtSecret();

        helloService.bindConfigAsFeature(
                SysConfigName.Safe.ALLOW_ANONYMOUS_COMMENT,
                FeatureName.ALLOW_ANONYMOUS_COMMENT,
                Boolean.class,
                Boolean.FALSE
        );

        return config;
    }

    /**
     * 初始化jwt密钥
     */
    private void initJwtSecret() {
        String secret = configService.getConfig(SysConfigName.Safe.TOKEN);
        if (secret == null) {
            secret = StringUtils.getRandomString(32, true);
            log.info("[初始化]生成token密钥");
            configService.setConfig(SysConfigName.Safe.TOKEN, secret);
        }
        JwtUtils.setSecret(secret);
        configService.addAfterSetListener(SysConfigName.Safe.TOKEN, JwtUtils::setSecret);
    }
}
