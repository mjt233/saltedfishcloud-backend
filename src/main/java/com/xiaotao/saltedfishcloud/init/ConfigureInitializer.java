package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.ConfigDao;
import com.xiaotao.saltedfishcloud.enums.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class ConfigureInitializer implements ApplicationRunner {
    @Resource
    private ConfigDao configDao;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        String storeType = configDao.getConfigure(ConfigName.STORE_TYPE);
        String regCode = configDao.getConfigure(ConfigName.REG_CODE);
        String syncDelay = configDao.getConfigure(ConfigName.SYNC_DELAY);
        if (storeType == null) {
            configDao.setConfigure(ConfigName.STORE_TYPE, DiskConfig.STORE_TYPE.toString());
            log.info("初始化存储模式记录：" + DiskConfig.STORE_TYPE);
            storeType = DiskConfig.STORE_TYPE.toString();
        }
        if (regCode == null) {
            configDao.setConfigure(ConfigName.REG_CODE, DiskConfig.REG_CODE);
            regCode = DiskConfig.REG_CODE;
            log.info("初始化邀请邀请码：" + regCode);
        }
        if (syncDelay == null) {
            syncDelay = DiskConfig.SYNC_DELAY + "";
            configDao.setConfigure(ConfigName.SYNC_DELAY, syncDelay);
        }

        DiskConfig.STORE_TYPE = StoreType.valueOf(storeType);
        DiskConfig.REG_CODE = regCode;
        DiskConfig.SYNC_DELAY = Integer.parseInt(syncDelay);
        log.info("[存储模式]："+ storeType);
        log.info("[注册邀请码]："+ regCode);
        log.info("[同步延迟]：" + syncDelay);
    }
}
