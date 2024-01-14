package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.nio.file.NoSuchFileException;

@RunWith(SpringRunner.class)
@SpringBootTest
class FileRecordServiceTest {
    @Resource
    private FileRecordService fileRecordService;
    @Resource
    private UserDao userDao;
    @Test
    void copy() throws NoSuchFileException {
        long uid = userDao.getUserByUser("xiaotao").getId();
        try {
            fileRecordService.copy(uid, "/", "/", uid, "f1", "234", true);
        } catch (UnsupportedOperationException ignore) {}
    }
}
