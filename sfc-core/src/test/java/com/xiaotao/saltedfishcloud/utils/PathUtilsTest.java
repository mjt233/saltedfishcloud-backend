package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.po.User;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@RunWith(SpringRunner.class)
class PathUtilsTest {
    @Resource
    private UserDao userDao;
    @Test
    void getRelativePath() {
        User user = userDao.getUserByUser("xiaotao");
        String path = DiskFileUtils.getRelativePath(User.getPublicUser(), DiskConfig.PUBLIC_ROOT + "/asdasd/asd/asd/zxv/z/asd");
        assertEquals("/asdasd/asd/asd/zxv/z/asd", path);
        String local = DiskConfig.rawPathHandler.getStorePath(user.getId(), "/a/b/c/aweq", null);
        System.out.println(local);
        path = DiskFileUtils.getRelativePath(user, local);
        assertEquals("/a/b/c/aweq", path);
    }

    @Test
    void getAllNode() {
        String[] allNode = PathUtils.getAllNode("/a/b/c");
        assertEquals("/a", allNode[0]);
        assertEquals("/a/b", allNode[1]);
        assertEquals("/a/b/c", allNode[2]);
    }
}
