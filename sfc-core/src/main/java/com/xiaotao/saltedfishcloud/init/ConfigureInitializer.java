package com.xiaotao.saltedfishcloud.init;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.service.config.version.VersionTag;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;


/**
 * 配置信息初始化器
 * todo 优化代码，封装每个配置项的配置和初始化流程
 */
@Component
@Slf4j
@Order(3)
@RequiredArgsConstructor
public class ConfigureInitializer implements ApplicationRunner {
    private final ConfigDao configDao;
    private final ConfigService configService;
    private final SysProperties sysProperties;
    private final HelloService helloService;
    @Override
    public void run(ApplicationArguments args) throws Exception {

        log.info("[当前系统版本]：" + sysProperties.getVersion());


        String storeMode = configDao.getConfigure(SysConfigName.Store.SYS_STORE_TYPE);
        String regCode = configDao.getConfigure(SysConfigName.Register.SYS_REGISTER_REG_CODE, sysProperties.getCommon().getRegCode());
        String syncDelay = configDao.getConfigure(SysConfigName.Store.SYNC_INTERVAL, sysProperties.getSync().getInterval() + "");

        boolean firstRun = storeMode == null;
        if (firstRun) {
            log.info("[初始化]存储模式记录：" + sysProperties.getStore().getMode());
            configDao.setConfigure(SysConfigName.Store.SYS_STORE_TYPE, sysProperties.getStore().getMode().toString());
            log.info("[初始化]邀请邀请码：" + regCode);
            configDao.setConfigure(SysConfigName.Register.SYS_REGISTER_REG_CODE, sysProperties.getCommon().getRegCode());
            log.info("[初始化]自动同步间隔：" + syncDelay);
            configDao.setConfigure(SysConfigName.Store.SYNC_INTERVAL, syncDelay);

            storeMode = sysProperties.getStore().getMode().toString();
        }

        String secret = configDao.getConfigure(SysConfigName.Safe.TOKEN);
        if (secret == null) {
            secret = StringUtils.getRandomString(32, true);
            log.info("[初始化]生成token密钥");
            configDao.setConfigure(SysConfigName.Safe.TOKEN, secret);
        }
        JwtUtils.setSecret(secret);

        // 服务器配置记录覆盖默认的开局配置记录
        sysProperties.getStore().setMode(storeMode);
        sysProperties.getCommon().setRegCode(regCode);
        sysProperties.getSync().setInterval(Integer.parseInt(syncDelay));
        log.info("[存储模式]："+ storeMode);
        log.info("[注册邀请码]："+ sysProperties.getCommon().getRegCode());
        log.info("[自动同步间隔]：" + syncDelay);
        if (sysProperties.getVersion().getTag() != VersionTag.RELEASE) {
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
        }



        // 将配置值直接绑定到系统特性描述
        this.bindConfigToFeature();

        // 订阅配置变更自定义策略
        this.subscribeConfigureChange();
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
    }

    /**
     * 订阅配置变更并同步到配置类
     */
    private void subscribeConfigureChange() {


        configService.addAfterSetListener(SysConfigName.Store.SYNC_INTERVAL, e -> sysProperties.getSync().setInterval(Integer.parseInt(e)));
        configService.addAfterSetListener(SysConfigName.Register.SYS_REGISTER_REG_CODE, e -> sysProperties.getCommon().setRegCode(e));
        configService.addBeforeSetListener(SysConfigName.Store.SYS_STORE_TYPE, e -> {
            try {
                configService.setStoreType(StoreMode.valueOf(e));
                sysProperties.getStore().setMode(e);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        configService.addAfterSetListener(SysConfigName.Safe.TOKEN, JwtUtils::setSecret);
    }
}
