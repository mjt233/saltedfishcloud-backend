package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.UserDao;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest
class FileRecordServiceTest {
    @Resource
    private FileRecordService fileRecordService;
    @Resource
    private UserDao userDao;
    @Test
    void copy() {
        int uid = userDao.getUserByUser("xiaotao").getId();
        try {
            fileRecordService.copy(uid, "/f1", "/new", uid, "233", true);
        } catch (UnsupportedOperationException ignore) {}
    }
}
