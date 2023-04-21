package com.xiaotao.saltedfishcloud.service.config;

import com.sfc.enums.StoreMode;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
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
    private ConfigServiceImpl configService;
    @Resource
    private ConfigDao configDao;

    @Test
    void setStoreType() throws IOException {
        configService.setStoreType(StoreMode.UNIQUE);
        configService.setStoreType(StoreMode.RAW);
        configService.setStoreType(StoreMode.RAW);
        configService.setStoreType(StoreMode.UNIQUE);
        configService.setStoreType(StoreMode.RAW);
    }

    @Test
    void getStoreType() {
    }
}
