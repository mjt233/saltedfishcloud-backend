package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;


@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
class DiskConfigTest {
    @Resource
    UserDao userDao;

    @Test
    void getPathHandler() {
        PathHandler pathHandler = DiskConfig.getPathHandler();
        BasicFileInfo fileInfo = FileInfo.getLocal("D:\\code\\JavaEEFrame\\saltedfishcloud\\咸鱼云Postman测试集合.postman_collection.json");
        String path = pathHandler.getStorePath(userDao.getUserByUser("xiaotao").getId(), "/", fileInfo);
        log.info(path);
    }
}
