package com.xiaotao.saltedfishcloud.service.sync;

import com.sfc.enums.StoreMode;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.service.config.ConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@SpringBootTest
@RunWith(SpringRunner.class)
class SyncServiceTest {
    @Resource
    private DefaultFileRecordSyncService defaultFileRecordSyncService;
    @Resource
    private ConfigServiceImpl configService;
    @Resource
    private UserDao userDao;

    @Test
    void syncLocal() throws Exception {
        configService.setStoreType(StoreMode.RAW);
//        syncService.syncLocal(User.getPublicUser());
        defaultFileRecordSyncService.doSync(userDao.getUserByUser("xiaotao").getId(), false);
    }
}
