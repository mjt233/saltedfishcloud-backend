package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
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
    private StoreServiceFactory storeService;
    @Resource
    private UserDao userDao;


    @Test
    void copy() throws IOException {
        long uid = userDao.getUserByUser("xiaotao").getId();
        storeService.getService().copy(uid, "/f1", "/", uid, "233", "f2", true);
    }
}
