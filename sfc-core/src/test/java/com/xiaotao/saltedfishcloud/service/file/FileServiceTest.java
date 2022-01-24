package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.IOException;

@SpringBootTest
public class FileServiceTest {
    @Resource
    DiskFileSystemFactory fileService;

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
    public void mkdirs() throws IOException {
        DiskFileSystem fileService = this.fileService.getFileSystem();
        fileService.mkdirs(1, "/a/b/c/d/e/f/g/h/j/k/l");
    }
}
