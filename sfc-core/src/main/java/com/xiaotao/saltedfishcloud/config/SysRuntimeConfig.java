package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.constant.MQTopicConstants;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 系统运行时配置信息
 * todo 考虑统一的配置缓存功能
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SysRuntimeConfig implements ApplicationRunner {
    private static ProtectLevel PROTECT_MODE_LEVEL = null;
    private static SysRuntimeConfig GLOBAL_HOLD_INST;

    @Autowired
    private ConfigService configService;

    @Autowired
    private MQService mqService;

    public static SysRuntimeConfig getInstance() {
        if (GLOBAL_HOLD_INST == null) {
            throw new IllegalStateException("SysRuntimeConfig 未实例化");
        }
        return GLOBAL_HOLD_INST;
    }

    public synchronized ProtectLevel getProtectModeLevel() {
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
    public synchronized void setProtectModeLevel(ProtectLevel level) {
        if ((level != ProtectLevel.OFF && level != null) && PROTECT_MODE_LEVEL != null) {
            throw new IllegalStateException("当前已处于：" + PROTECT_MODE_LEVEL);
        }
        if (level != null && level != ProtectLevel.OFF) {
            log.debug("保护级别切换到" + level);
        } else {
            log.debug("关闭保护模式");
            level = ProtectLevel.OFF;
        }

        // 发布保护模式切换消息
        mqService.sendBroadcast(MQTopicConstants.PROTECT_LEVEL_SWITCH, level);
    }

    protected void changeProtectModeLevel(ProtectLevel level) {
        if (level == ProtectLevel.OFF) {
            PROTECT_MODE_LEVEL = null;
        } else {
            PROTECT_MODE_LEVEL = level;
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        fetchConfig();
        SysRuntimeConfig.GLOBAL_HOLD_INST = this;

        this.listenStoreTypeChange();
    }

    /**
     * 监听分布式集群中触发保护级别切换信号
     */
    private void listenStoreTypeChange() {
        mqService.subscribeBroadcast(MQTopicConstants.PROTECT_LEVEL_SWITCH, msg -> {
            ProtectLevel level = msg.body();
            log.debug("[Runtime Config]因消息通知,系统保护级别切换为：{}", level);
            changeProtectModeLevel(level);
        });
    }



    /**
     * 从数据库中抓取数据更新配置
     */
    public void fetchConfig() {
        Boolean enableRegCode = configService.getConfig(SysCommonConfig::getEnableRegCode);
        Boolean enableEmailReg = configService.getConfig(SysCommonConfig::getEnableEmailReg);
        if (enableRegCode == null && enableEmailReg == null) {
            configService.setConfig(SysCommonConfig::getEnableRegCode, Boolean.TRUE);
            configService.setConfig(SysCommonConfig::getEnableEmailReg, Boolean.FALSE);
            log.info("[注册规则初始化]未检测到注册规则配置，默认开启邀请码注册");
        }
    }
}
