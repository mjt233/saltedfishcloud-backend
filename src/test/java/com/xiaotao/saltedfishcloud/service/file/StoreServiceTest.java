package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.UserDao;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import java.io.IOException;


@SpringBootTest
@RunWith(SpringRunner.class)
class StoreServiceTest {
    @Resource
    private StoreService storeService;
    @Resource
    private UserDao userDao;


    @Test
    void copy() throws IOException {
        int uid = userDao.getUserByUser("xiaotao").getId();
        storeService.copy(uid, "/f1", "/", uid, "233", "f2", true);
    }
}
