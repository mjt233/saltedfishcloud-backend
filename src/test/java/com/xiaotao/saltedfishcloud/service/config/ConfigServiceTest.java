package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.ConfigDao;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import java.io.IOException;

@SpringBootTest
@RunWith(SpringRunner.class)
class ConfigServiceTest {
    @Resource
    private ConfigService configService;
    @Resource
    private ConfigDao configDao;

    @Test
    void setStoreType() throws IOException {
        configService.setStoreType(StoreType.UNIQUE);
        configService.setStoreType(StoreType.RAW);
        configService.setStoreType(StoreType.RAW);
        configService.setStoreType(StoreType.UNIQUE);
        configService.setStoreType(StoreType.RAW);
    }

    @Test
    void getStoreType() {
    }
}
