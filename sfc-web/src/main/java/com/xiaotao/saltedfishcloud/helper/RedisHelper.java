package com.xiaotao.saltedfishcloud.helper;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisHelper {
    public final static String prefix = "xyy:";
    private final StringRedisTemplate redisTemplate;

    public RedisHelper(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 设置一个Redis记录，默认超时15分钟
     * @param key  键
     * @param value 值
     */
    public void set(String key, String value) {
        set(key, value, 900);
    }

    /**
     * 设置一个Redis记录
     * @param key 键
     * @param value 值
     * @param timeout 超时时间，单位秒
     */
    public void set(String key, String value, int timeout) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(timeout));
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(getKey(key));
    }

    public void delete(String key) {
        redisTemplate.delete(getKey(key));
    }

    private String getKey(String key) {
        return prefix + key;
    }
}
