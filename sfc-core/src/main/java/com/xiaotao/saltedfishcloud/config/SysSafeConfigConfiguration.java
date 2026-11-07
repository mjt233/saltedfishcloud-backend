package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.model.config.SysSafeConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.sfc.common.utils.PropertyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 系统安全配置自动注册
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class SysSafeConfigConfiguration {
    /**
     * JWT 会话密钥要求的字符长度，与网关（sfc-gateway）验签侧的校验保持一致
     */
    private static final int JWT_SECRET_LENGTH = 32;

    private final ConfigService configService;
    private final HelloService helloService;
    private final SysProperties sysProperties;

    @Bean
    public SysSafeConfig sysSafeConfig() {
        SysSafeConfig config = new SysSafeConfig();
        configService.bindPropertyEntity(config);

        initJwtSecret();

        helloService.bindConfigAsFeature(
                PropertyUtils.parseLambdaConfigName(SysSafeConfig::getAllowAnonymousComments),
                FeatureName.ALLOW_ANONYMOUS_COMMENT,
                Boolean.class,
                Boolean.FALSE
        );

        return config;
    }

    /**
     * 初始化 JWT 会话密钥。
     * <p>密钥来自配置项 {@code sys.jwt-secret}（推荐通过环境变量 {@code SFC_JWT_SECRET} 注入），
     * 不再写入数据库配置表，也不再自动随机生成：未配置或长度不是 32 个字符时直接抛出异常使应用启动失败，
     * 避免 core 与网关（sfc-gateway）使用不同密钥导致的验签漂移。</p>
     *
     * @throws IllegalStateException 未配置密钥，或密钥长度不是 32 个字符时抛出
     */
    private void initJwtSecret() {
        String secret = sysProperties.getJWT_SECRET();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("未配置 JWT 密钥：请设置环境变量 SFC_JWT_SECRET，"
                    + "或在配置文件中配置 sys.jwt-secret（必须恰好 " + JWT_SECRET_LENGTH + " 个字符）");
        }
        if (secret.length() != JWT_SECRET_LENGTH) {
            throw new IllegalStateException("JWT 密钥长度必须恰好为 " + JWT_SECRET_LENGTH + " 个字符（当前为 "
                    + secret.length() + " 个字符），来源：环境变量 SFC_JWT_SECRET 或配置项 sys.jwt-secret");
        }
        JwtUtils.setSecret(secret);
        log.info("[初始化]已从配置装载 JWT 会话密钥（长度 {}），来源：sys.jwt-secret / 环境变量 SFC_JWT_SECRET",
                JWT_SECRET_LENGTH);
    }
}
