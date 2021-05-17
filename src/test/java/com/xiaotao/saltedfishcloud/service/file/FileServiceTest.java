package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class FileServiceTest {
    @Resource
    FileService fileService;
    @Resource
    ConfigService configService;

    @Resource
    UserDao userDao;

    @Test
    public void move() {
        try {
            int uid = userDao.getUserByUser("xiaotao").getId();
            fileService.mkdir(uid, "/", "test");
            fileService.mkdir(uid, "/", "test2");
            fileService.move(uid, "/", "/test", "test2", true);
            fileService.move(uid, "/", "/test/test2", "ml.exe", true);
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

    @Test
    public void getLocalFilePathByMD5() throws IOException {
        configService.setStoreType(StoreType.RAW);
        FileInfo f1 = fileService.getFileByMD5("b83294df4d6c5643853e3148132f2af5");
        configService.setStoreType(StoreType.UNIQUE);
        FileInfo f2 = fileService.getFileByMD5("b83294df4d6c5643853e3148132f2af5");
        try {
            configService.setStoreType(StoreType.RAW);
            fileService.getFileByMD5("asdca");
            throw new RuntimeException("测试失败");
        } catch (NoSuchFileException ignore) {
        }
        try {
            configService.setStoreType(StoreType.UNIQUE);
            fileService.getFileByMD5("asdca");
            throw new RuntimeException("测试失败");
        } catch (NoSuchFileException ignore) {
        }
        System.out.println(f1.getPath());
        System.out.println(f2.getPath());
    }
}
