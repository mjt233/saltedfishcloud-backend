package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.dao.NodeDao;
import com.xiaotao.saltedfishcloud.service.RedisTestService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest
class RedisConfigTest {
    @Resource
    private RedisTestService redisTestService;
    @Resource
    private CacheManager cacheManager;
    @Resource
    private NodeDao nodeDao;

    public void testNodeHandle() {

    }

    @Test
    public void testRedis() throws InterruptedException {
        System.out.println(cacheManager.getClass().getName());
        redisTestService.getUser(123);
        redisTestService.getUser(123);
        redisTestService.getUser(123);
        redisTestService.setToken("322");
        redisTestService.setToken("322");
    }
}
