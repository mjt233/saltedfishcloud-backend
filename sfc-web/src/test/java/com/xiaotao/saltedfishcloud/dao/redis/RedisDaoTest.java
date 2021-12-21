package com.xiaotao.saltedfishcloud.dao.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@SuppressWarnings("unused")
class RedisDaoTest {

    @Autowired
    private RedisDao redisDao;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void scanKeys() {
        redisDao.scanKeys("*").forEach(System.out::println);
    }

    @Test
    void decrementAndGet() {
        String key = "test_key";
        redisTemplate.opsForValue().set(key, 1);
        Object v = redisTemplate.opsForValue().get(key);
        Long res = redisDao.decrementAndGet(key, 1, 0);
        assertEquals(0, res);

        redisTemplate.opsForValue().set(key, 100);
        res = redisDao.decrementAndGet(key, 99, 0);
        assertEquals(1, res);


        assertNull(redisDao.decrementAndGet(key, 5, 0));
        redisTemplate.delete("test_key");
    }
}
