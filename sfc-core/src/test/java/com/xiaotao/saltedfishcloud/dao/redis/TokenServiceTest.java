package com.xiaotao.saltedfishcloud.dao.redis;

import com.xiaotao.saltedfishcloud.dao.jpa.UserRepo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TokenServiceTest {

    @Resource
    private TokenServiceImpl tokenDao;
    @Resource
    private UserRepo userRepo;
    @Resource
    private UserService userService;

    @Test
    public void test() {
        User user = userRepo.getUserByUser("admin");
        String token = tokenDao.generateUserToken(user);
        tokenDao.setToken(user.getId(), token);
        assertTrue(tokenDao.isTokenValid(user.getId(), token));
        userService.modifyPasswd(user.getId(), "admin233", "admin666");
        assertFalse(tokenDao.isTokenValid(user.getId(), token));
        userService.modifyPasswd(user.getId(), "admin666", "admin233");
    }
}
