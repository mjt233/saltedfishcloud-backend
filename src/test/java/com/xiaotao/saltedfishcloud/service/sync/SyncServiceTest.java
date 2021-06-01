package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.IOException;

@SpringBootTest
@RunWith(SpringRunner.class)
class SyncServiceTest {
    @Resource
    private SyncService syncService;
    @Resource
    private ConfigService configService;
    @Resource
    private UserDao userDao;

    @Test
    void syncLocal() throws IOException {
        configService.setStoreType(StoreType.RAW);
//        syncService.syncLocal(User.getPublicUser());
        syncService.syncLocal(userDao.getUserByUser("xiaotao"));
    }
}
