package com.xiaotao.saltedfishcloud.service.file;

import javax.annotation.Resource;

import com.xiaotao.saltedfishcloud.dao.UserDao;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class FileServiceTest {
    @Resource
    FileService fileService;

    @Resource
    UserDao userDao;

    @Test
    public void move() {
        try {
            int uid = userDao.getUserByUser("xiaotao").getId();
            fileService.mkdir(uid, "/", "test");
            fileService.mkdir(uid, "/", "test2");
            fileService.move(uid, "/", "/test", "test2");
            fileService.move(uid, "/", "/test/test2", "ml.exe");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void copy() {
        int uid = userDao.getUserByUser("xiaotao").getId();
        try {
            fileService.copy(uid, "/", "/", uid, "f1", "f2", true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
