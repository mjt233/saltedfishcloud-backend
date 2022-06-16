package com.xiaotao.saltedfishcloud.dao.redis;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TokenDaoTest {

    @Resource
    private TokenDaoImpl tokenDao;
    @Resource
    private UserDao userDao;
    @Resource
    private UserService userService;

    @Test
    public void test() {
        User user = userDao.getUserByUser("admin");
        String token = user.getToken();
        tokenDao.setToken(user.getId(), token);
        assertTrue(tokenDao.isTokenValid(user.getId(), token));
        userService.modifyPasswd(user.getId(), "admin233", "admin666");
        assertFalse(tokenDao.isTokenValid(user.getId(), token));
        userService.modifyPasswd(user.getId(), "admin666", "admin233");
    }
}
