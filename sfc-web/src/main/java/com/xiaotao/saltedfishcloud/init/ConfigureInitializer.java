package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.CommandLineOption;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigServiceImpl;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.service.config.version.VersionTag;
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
    private final ConfigServiceImpl configService;
    private final CommandLineOption commandLineOption;
    private final SysRuntimeConfig sysRuntimeConfig;
    @Override
    public void run(ApplicationArguments args) throws Exception {

        log.info("[当前系统版本]：" + DiskConfig.VERSION);
        String storeType = configDao.getConfigure(ConfigName.STORE_TYPE);
        String regCode = configDao.getConfigure(ConfigName.REG_CODE, DiskConfig.REG_CODE);
        String syncDelay = configDao.getConfigure(ConfigName.SYNC_DELAY, DiskConfig.SYNC_DELAY + "");
        Version version = Version.valueOf(configDao.getConfigure(ConfigName.VERSION, "1.0.0-SNAPSHOT"));

        boolean firstRun = storeType == null;
        if (firstRun) {
            log.info("[初始化]存储模式记录：" + DiskConfig.STORE_TYPE);
            configDao.setConfigure(ConfigName.STORE_TYPE, DiskConfig.STORE_TYPE.toString());
            log.info("[初始化]邀请邀请码：" + regCode);
            configDao.setConfigure(ConfigName.REG_CODE, DiskConfig.REG_CODE);
            log.info("[初始化]同步延迟：" + syncDelay);
            configDao.setConfigure(ConfigName.SYNC_DELAY, syncDelay);

            storeType = DiskConfig.STORE_TYPE.toString();
        } else {
            String modeSwitch = commandLineOption.getValue(CommandLineOption.SWITCH);
            if (modeSwitch != null) {
                if (!configService.setStoreType(StoreType.valueOf(modeSwitch))) {
                    log.warn("系统当前已处于" + modeSwitch + "存储模式下，切换行为已忽略");
                } else {
                    log.info("存储已切换为：" + modeSwitch + ", 任务执行完毕");
                }
                System.exit(0);
            }
            if (Version.getEarliestVersion().equals(version) && storeType.equals(StoreType.UNIQUE.toString())) {
                System.out.println("旧版本咸鱼云1.0.0不支持在UNIQUE（唯一）存储状态下升级版本，请先转化为RAW（原始）存储模式");
                System.out.println("启动咸鱼云时使用命令行参数--switch=RAW可启动切换程序，但首先还是建议您备份好您的数据防止丢失");
                System.out.println("The old version salted fish cloud(1.0.0) is unsupported upgrade to current version " + DiskConfig.VERSION + " in the UNIQUE store mode, your must switch to RAW store mode");
                System.out.println("You can launch salted fish cloud with command line argument --switch=RAW to launch switch task, but may be you should backup your data first avoid data lose");
                throw new IllegalStateException("Unable to upgrade from 1.0.0 in UNIQUE store mode");
            }
        }

        String secret = configDao.getConfigure(ConfigName.TOKEN_SECRET);
        if (secret == null) {
            secret = StringUtils.getRandomString(32, true);
            log.info("[初始化]生成token密钥");
            configDao.setConfigure(ConfigName.TOKEN_SECRET, secret);
        }
        JwtUtils.setSecret(secret);

        // 服务器配置记录覆盖默认的开局配置记录
        DiskConfig.STORE_TYPE = StoreType.valueOf(storeType);
        DiskConfig.REG_CODE = regCode;
        DiskConfig.SYNC_DELAY = Integer.parseInt(syncDelay);
        log.info("[存储模式]："+ storeType);
        log.info("[注册邀请码]："+ regCode);
        log.info("[同步延迟]：" + syncDelay);
        if (DiskConfig.VERSION.getTag() != VersionTag.RELEASE) {
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
            log.warn("正在使用非发行版本，系统运行可能存在不稳定甚至出现数据损坏，请勿用于线上正式环境");
        }

        if (!sysRuntimeConfig.isEnableEmailReg() && !sysRuntimeConfig.isEnableRegCode()) {
            log.warn("[注册关闭]系统未开启任何用户注册方式");
        }
    }
}
