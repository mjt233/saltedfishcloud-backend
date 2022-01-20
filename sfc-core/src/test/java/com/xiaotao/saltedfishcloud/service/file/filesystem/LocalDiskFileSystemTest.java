package com.xiaotao.saltedfishcloud.service.file.filesystem;

import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.DigestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@RequiredArgsConstructor
class LocalDiskFileSystemTest {
    @Autowired
    private DiskFileSystemFactory factory;
    @Autowired
    private UserService userService;

    @Test
    public void testExtract() throws IOException {
        User admin = userService.getUserByUser("admin");
        DiskFileSystem fileSystem = factory.getFileSystem();
        ClassPathResource resource = new ClassPathResource("/test/test.zip");
        String md5 = DigestUtils.md5DigestAsHex(resource.getInputStream());

        // 先上传文件到网盘
        FileInfo fileInfo = new FileInfo();
        fileInfo.setMd5(md5);
        fileInfo.setName("test.zip");
        fileInfo.setSize(resource.contentLength());

        fileSystem.mkdirs(admin.getId(), "/test");
        fileSystem.saveFile(admin.getId(), resource.getInputStream(), "/test", fileInfo);

        fileSystem.extractArchive(admin.getId(), "/test", "test.zip", "/extracttest");
        assertTrue( fileSystem.exist(admin.getId(), "/extracttest/a.txt"));
    }
}
