package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 系统运行时配置信息，DiskConfig中的属性将逐步转移至这里
 */
@Component
@Slf4j
public class SysRuntimeConfig {
    private static SysRuntimeConfig GLOBAL_HOLD_INST;
    private final ConfigService configService;

    @Getter
    private boolean enableRegCode;
    @Getter
    private boolean enableEmailReg;

    public static SysRuntimeConfig getInstance() {
        return GLOBAL_HOLD_INST;
    }

    public SysRuntimeConfig(ConfigService configService) {
        this.configService = configService;
        fetchConfig();
        SysRuntimeConfig.GLOBAL_HOLD_INST = this;

        log.info("[注册规则]当前注册规则配置： 注册邀请码 - {} 邮箱注册 - {}", enableRegCode ? '√': 'X', enableEmailReg ? '√': 'X');

        // 监听配置改变，实时更新状态缓存
        configService.addConfigChangeListener(e -> {
            ConfigName key = e.getKey();
            if (key == ConfigName.ENABLE_EMAIL_REG) {
                enableEmailReg = "TRUE".equals(e.getValue());
            } else if (key == ConfigName.REG_CODE) {
                enableRegCode = "TRUE".equals(e.getValue());
            }
        });
    }

    /**
     * 从数据库中抓取数据更新配置
     */
    public void fetchConfig() {
        Map<ConfigName, String> configCache = configService.getAllConfig();
        String enableRegCode = configCache.get(ConfigName.ENABLE_REG_CODE);
        String enableEmailReg = configCache.get(ConfigName.ENABLE_EMAIL_REG);
        if (enableRegCode == null && enableEmailReg == null) {
            enableRegCode = "TRUE";
            try {
                configService.setConfig(ConfigName.ENABLE_REG_CODE, "TRUE");
                configService.setConfig(ConfigName.ENABLE_EMAIL_REG, "FALSE");
                log.info("[注册规则初始化]未检测到注册规则配置，默认开启邀请码注册");
            } catch (IOException ignore) { }
        }
        this.enableRegCode = "TRUE".equals(enableRegCode);
        this.enableEmailReg = "TRUE".equals(enableEmailReg);
    }
}
