package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

@SpringBootTest
public class FileServiceTest {
    @Resource
    DiskFileSystemFactory fileService;
    @Resource
    ConfigService configService;

    @Resource
    UserDao userDao;

    @Test
    public void move() {
        try {
            DiskFileSystem fileService = this.fileService.getFileSystem();
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
            fileService.getFileSystem().copy(uid, "/", "/", uid, "f1", "f2", true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void getLocalFilePathByMD5() throws IOException {
        configService.setStoreType(StoreType.RAW);
        DiskFileSystem fileService = this.fileService.getFileSystem();
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

    @Test
    public void mkdirs() throws IOException {
        DiskFileSystem fileService = this.fileService.getFileSystem();
        fileService.mkdirs(1, "/a/b/c/d/e/f/g/h/j/k/l");
    }
}
