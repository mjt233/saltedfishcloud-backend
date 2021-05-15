package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.ConfigDao;
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
        String dbStoreType = configDao.getConfigure(StoreType.getConfigKey());
        if (dbStoreType == null) {
            configDao.setConfigure(StoreType.getConfigKey(), DiskConfig.STORE_TYPE.toString());
            log.info("初始化配置表： " + StoreType.getConfigKey() + ":" + DiskConfig.STORE_TYPE);
            return;
        }
        StoreType storeType = StoreType.valueOf(dbStoreType);
        if (storeType == DiskConfig.STORE_TYPE) {
            log.info("存储方式：" + DiskConfig.STORE_TYPE);
        } else {
            log.warn("应用参数存储方式与配置表数据不一致： 配置表 - " + dbStoreType + "| 应用参数 - " +  DiskConfig.STORE_TYPE);
            log.warn("存储方式将以配置表为准运行");
            DiskConfig.STORE_TYPE = storeType;
        }
    }
}
