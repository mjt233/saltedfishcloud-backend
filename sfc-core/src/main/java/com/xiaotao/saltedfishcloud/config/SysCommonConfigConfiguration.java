package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Map;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class SysCommonConfigConfiguration {
    private final HelloService helloService;
    private final ConfigService configService;

    @Bean
    public SysCommonConfig sysCommonConfig(ConfigService configService) {
        SysCommonConfig config = new SysCommonConfig();
        configService.bindPropertyEntity(config);

        log.info("[注册规则]当前注册规则配置： 注册邀请码 - {} 邮箱注册 - {}", config.getEnableRegCode() ? '√': 'X', config.getEnableEmailReg() ? '√': 'X');
        if (!config.getEnableEmailReg() && !config.getEnableRegCode()) {
            log.warn("[注册关闭]系统未开启任何用户注册方式");
        }

        // 初始化jwt密钥
        this.initJwtSecret();

        // 将配置值直接绑定到系统特性描述
        this.bindConfigToFeature();

        // 监听存储类型变更事件，切换存储服务
        this.listenStoreTypeChange();

        return config;
    }

    /**
     * 监听存储类型变更事件，切换存储服务
     */
    private void listenStoreTypeChange() {
        configService.addBeforeSetListener(SysConfigName.Store.SYS_STORE_TYPE, e -> {
            try {
                configService.setStoreType(StoreMode.valueOf(e));
            } catch (IOException ex) {
                log.error("存储类型切换出错", ex);
            }
        });
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

    /**
     * 将配置项与系统特性描述绑定
     */
    private void bindConfigToFeature() {
        // 是否默认黑暗主题
        helloService.bindConfigAsFeature(SysConfigName.Theme.DARK, FeatureName.DARK_THEME, Boolean.class);

        // 默认背景图配置
        helloService.bindConfigAsFeature(SysConfigName.Bg.SYS_BG_MAIN, FeatureName.BG_MAIN, Map.class);

        // 是否允许邮箱/注册码注册
        helloService.bindConfigAsFeature(SysConfigName.Register.ENABLE_EMAIL_REG, FeatureName.ENABLE_EMAIL_REG, Boolean.class);
        helloService.bindConfigAsFeature(SysConfigName.Register.ENABLE_REG_CODE, FeatureName.ENABLE_REG_CODE, Boolean.class);

        // 匿名留言开关
        helloService.bindConfigAsFeature(SysConfigName.Safe.ALLOW_ANONYMOUS_COMMENT, FeatureName.ALLOW_ANONYMOUS_COMMENT, Boolean.class);

        // 网盘文件上传是否使用通用资源请求接口
        helloService.bindConfigAsFeature(SysCommonConfig::getIsUseCommonUpload, FeatureName.IS_USE_COMMON_UPLOAD);
    }
}
