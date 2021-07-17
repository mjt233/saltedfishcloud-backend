package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.ConfigDao;
import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.StaticFileService;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
@Order(1)
public class ConfigureInitializer implements ApplicationRunner {
    @Resource
    private ConfigDao configDao;
    @Resource
    private FileService fileService;
    @Resource
    private UserDao userDao;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        StaticFileService.fileService = fileService;
        StaticFileService.userDao = userDao;

        log.info("[当前系统版本]：" + DiskConfig.VERSION);
        String storeType = configDao.getConfigure(ConfigName.STORE_TYPE, DiskConfig.STORE_TYPE.toString());
        String regCode = configDao.getConfigure(ConfigName.REG_CODE, DiskConfig.REG_CODE);
        String syncDelay = configDao.getConfigure(ConfigName.SYNC_DELAY, DiskConfig.SYNC_DELAY + "");
        Version version = Version.load(configDao.getConfigure(ConfigName.VERSION, "1.0.0-SNAPSHOT"));

        boolean firstRun = storeType == null;
        if (firstRun) {
            log.info("[初始化]存储模式记录：" + DiskConfig.STORE_TYPE);
            configDao.setConfigure(ConfigName.STORE_TYPE, DiskConfig.STORE_TYPE.toString());
            log.info("[初始化]邀请邀请码：" + regCode);
            configDao.setConfigure(ConfigName.REG_CODE, DiskConfig.REG_CODE);
            log.info("[初始化]同步延迟：" + syncDelay);
            configDao.setConfigure(ConfigName.SYNC_DELAY, syncDelay);

            storeType = DiskConfig.STORE_TYPE.toString();
        } else if (Version.getEarliestVersion().equals(version) && storeType.equals(StoreType.UNIQUE.toString())) {
            System.out.println("旧版本咸鱼云1.0.0不支持在UNIQUE（唯一）存储状态下升级版本，请先转化为RAW（原始）存储模式");
            System.out.println("The old version salted fish cloud(1.0.0) is unsupported upgrade to current version " + DiskConfig.VERSION + " in the UNIQUE store mode, your must switch to RAW store mode");
            throw new IllegalArgumentException("Unable to upgrade from 1.0.0 in UNIQUE store mode");
        }

        // 服务器配置记录覆盖默认的开局配置记录
        DiskConfig.STORE_TYPE = StoreType.valueOf(storeType);
        DiskConfig.REG_CODE = regCode;
        DiskConfig.SYNC_DELAY = Integer.parseInt(syncDelay);
        log.info("[存储模式]："+ storeType);
        log.info("[注册邀请码]："+ regCode);
        log.info("[同步延迟]：" + syncDelay);
    }
}
