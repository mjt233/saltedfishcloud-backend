package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 系统运行时配置信息，DiskConfig中的属性将逐步转移至这里
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SysRuntimeConfig implements ApplicationRunner {
    public static ProtectLevel PROTECT_MODE_LEVEL = null;
    private static SysRuntimeConfig GLOBAL_HOLD_INST;

    @Autowired
    private ConfigService configService;

    @Getter
    private boolean enableRegCode;
    @Getter
    private boolean enableEmailReg;

    public static SysRuntimeConfig getInstance() {
        if (GLOBAL_HOLD_INST == null) {
            throw new IllegalStateException("SysRuntimeConfig 未实例化");
        }
        return GLOBAL_HOLD_INST;
    }

    public static synchronized ProtectLevel getProtectModeLevel() {
        return PROTECT_MODE_LEVEL;
    }

    /**
     * 切换系统的保护模式级别<br>
     * NOTE: 只能在保护模式的开与关之间切换，无法从保护模式的某一级别切换到另一级别<br>
     * 例：<br>
     *     null -> DATA_MOVING          [OK]<br>
     *     null -> DATA_CHECKING        [OK]<br>
     *     DATA_MOVING -> null          [OK]<br>
     *     DATA_CHECKING -> null        [OK]<br>
     *     DATA_MOVING -> DATA_CHECKING <strong>[!NO!]</strong> <br>
     *     DATA_CHECKING -> DATA_MOVING <strong>[!NO!]</strong>
     * @param level 保护模式级别
     * @throws IllegalStateException 当系统处于保护模式下抛出此异常
     */
    public static synchronized void setProtectModeLevel(ProtectLevel level) {
        if (level != null && PROTECT_MODE_LEVEL != null) {
            throw new IllegalStateException("当前已处于：" + PROTECT_MODE_LEVEL);
        }
        if (level != null) {
            log.debug("保护级别切换到" + level);
        } else {
            log.debug("关闭保护模式");
        }
        PROTECT_MODE_LEVEL = level;
    }

    @Override
    public void run(ApplicationArguments args) {
        fetchConfig();
        SysRuntimeConfig.GLOBAL_HOLD_INST = this;

        log.info("[注册规则]当前注册规则配置： 注册邀请码 - {} 邮箱注册 - {}", enableRegCode ? '√': 'X', enableEmailReg ? '√': 'X');

        // 监听配置改变，实时更新状态缓存
        configService.addConfigSetListener(e -> {
            ConfigName key = e.getKey();
            if (key == ConfigName.ENABLE_EMAIL_REG) {
                enableEmailReg = "true".equals(e.getValue().toLowerCase());
            } else if (key == ConfigName.ENABLE_REG_CODE) {
                enableRegCode = "true".equals(e.getValue().toLowerCase());
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
            enableRegCode = "true";
            try {
                configService.setConfig(ConfigName.ENABLE_REG_CODE, "true");
                configService.setConfig(ConfigName.ENABLE_EMAIL_REG, "false");
                log.info("[注册规则初始化]未检测到注册规则配置，默认开启邀请码注册");
            } catch (IOException ignore) { }
        }
        this.enableRegCode = "true".equals(enableRegCode == null ? null : enableRegCode.toLowerCase());
        this.enableEmailReg = "true".equals(enableEmailReg == null ? null : enableEmailReg.toLowerCase());
    }
}
