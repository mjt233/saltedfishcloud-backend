package com.xiaotao.saltedfishcloud.dao.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@SuppressWarnings("unused")
class RedisDaoTest {

    @Autowired
    private RedisDao redisDao;

    @Test
    void scanKeys() {
        redisDao.scanKeys("*").forEach(System.out::println);
    }
}
