package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.dao.jpa.UserRepo;
import com.xiaotao.saltedfishcloud.service.config.ConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

@SpringBootTest
@RunWith(SpringRunner.class)
class SyncServiceTest {
    @Resource
    private DefaultFileRecordSyncService defaultFileRecordSyncService;
    @Resource
    private ConfigServiceImpl configService;
    @Resource
    private UserRepo userRepo;

    @Test
    void syncLocal() throws Exception {
        configService.setStoreType(StoreMode.RAW);
//        syncService.syncLocal(User.getPublicUser());
        defaultFileRecordSyncService.doSync(userRepo.getUserByUser("xiaotao").getId(), false);
    }
}
