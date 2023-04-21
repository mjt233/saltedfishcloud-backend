package com.xiaotao.saltedfishcloud.config;

import com.sfc.constant.MQTopic;
import com.sfc.enums.ProtectLevel;
import com.sfc.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
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
    private static ProtectLevel PROTECT_MODE_LEVEL = null;
    private static SysRuntimeConfig GLOBAL_HOLD_INST;

    @Autowired
    private ConfigService configService;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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
        redisTemplate.convertAndSend(MQTopic.PROTECT_LEVEL_SWITCH, level.toString());
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

        log.info("[注册规则]当前注册规则配置： 注册邀请码 - {} 邮箱注册 - {}", enableRegCode ? '√': 'X', enableEmailReg ? '√': 'X');
        if (!this.isEnableEmailReg() && !this.isEnableRegCode()) {
            log.warn("[注册关闭]系统未开启任何用户注册方式");
        }

        this.listenStoreTypeChange();
    }

    /**
     * 监听分布式集群中触发保护级别切换信号
     */
    private void listenStoreTypeChange() {

        redisMessageListenerContainer.addMessageListener((message, pattern) -> {
            ProtectLevel level = ProtectLevel.valueOf((String)redisTemplate.getValueSerializer().deserialize(message.getBody()));
            log.debug("[Runtime Config]因消息通知,系统保护级别切换为：{}", level);
            changeProtectModeLevel(level);
        }, new PatternTopic(MQTopic.PROTECT_LEVEL_SWITCH));
    }



    /**
     * 从数据库中抓取数据更新配置
     */
    public void fetchConfig() {
        Map<String, String> configCache = configService.getAllConfig();
        String enableRegCode = configCache.get(SysConfigName.Register.ENABLE_REG_CODE);
        String enableEmailReg = configCache.get(SysConfigName.Register.ENABLE_EMAIL_REG);
        if (enableRegCode == null && enableEmailReg == null) {
            enableRegCode = "true";
            try {
                configService.setConfig(SysConfigName.Register.ENABLE_REG_CODE, "true");
                configService.setConfig(SysConfigName.Register.ENABLE_EMAIL_REG, "false");
                log.info("[注册规则初始化]未检测到注册规则配置，默认开启邀请码注册");
            } catch (IOException ignore) { }
        }
        this.enableRegCode = "true".equals(enableRegCode == null ? null : enableRegCode.toLowerCase());
        this.enableEmailReg = "true".equals(enableEmailReg == null ? null : enableEmailReg.toLowerCase());
    }
}
