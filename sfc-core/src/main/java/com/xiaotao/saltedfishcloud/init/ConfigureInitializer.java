package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.version.VersionTag;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;


/**
 * 配置信息初始化器
 * @TODO 优化代码，封装每个配置项的配置和初始化流程
 */
@Component
@Slf4j
@Order(2)
@RequiredArgsConstructor
public class ConfigureInitializer implements ApplicationRunner {
    private final ConfigDao configDao;
    private final SysRuntimeConfig sysRuntimeConfig;
    private final SysProperties sysProperties;
    @Override
    public void run(ApplicationArguments args) throws Exception {

        log.info("[当前系统版本]：" + sysProperties.getVersion());
        String storeType = configDao.getConfigure(ConfigName.STORE_TYPE);
        String regCode = configDao.getConfigure(ConfigName.REG_CODE, sysProperties.getCommon().getRegCode());
        String syncDelay = configDao.getConfigure(ConfigName.SYNC_DELAY, LocalStoreConfig.SYNC_DELAY + "");

        boolean firstRun = storeType == null;
        if (firstRun) {
            log.info("[初始化]存储模式记录：" + LocalStoreConfig.STORE_TYPE);
            configDao.setConfigure(ConfigName.STORE_TYPE, LocalStoreConfig.STORE_TYPE.toString());
            log.info("[初始化]邀请邀请码：" + regCode);
            configDao.setConfigure(ConfigName.REG_CODE, sysProperties.getCommon().getRegCode());
            log.info("[初始化]同步延迟：" + syncDelay);
            configDao.setConfigure(ConfigName.SYNC_DELAY, syncDelay);

            storeType = LocalStoreConfig.STORE_TYPE.toString();
        }

        String secret = configDao.getConfigure(ConfigName.TOKEN_SECRET);
        if (secret == null) {
            secret = StringUtils.getRandomString(32, true);
            log.info("[初始化]生成token密钥");
            configDao.setConfigure(ConfigName.TOKEN_SECRET, secret);
        }
        JwtUtils.setSecret(secret);

        // 服务器配置记录覆盖默认的开局配置记录
        LocalStoreConfig.STORE_TYPE = StoreType.valueOf(storeType);
        sysProperties.getCommon().setRegCode(regCode);
        LocalStoreConfig.SYNC_DELAY = Integer.parseInt(syncDelay);
        log.info("[存储模式]："+ storeType);
        log.info("[注册邀请码]："+ sysProperties.getCommon().getRegCode());
        log.info("[同步延迟]：" + syncDelay);
        if (sysProperties.getVersion().getTag() != VersionTag.RELEASE) {
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
        }

        if (!sysRuntimeConfig.isEnableEmailReg() && !sysRuntimeConfig.isEnableRegCode()) {
            log.warn("[注册关闭]系统未开启任何用户注册方式");
        }
    }
}
