package com.xiaotao.saltedfishcloud.dao.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisDao {
    private final RedisTemplate<String, Object> redisTemplate;
    /**
     * 通过表达式使用scan方法扫描匹配的key（而不是keys）
     * @param pattern   key匹配表达式
     * @return          匹配的key集合
     */
    public Set<String> scanKeys(String pattern) {
        ScanOptions opts = ScanOptions.scanOptions().match(pattern).count(1000).build();
        Set<String> res = new HashSet<>();
        return redisTemplate.execute((RedisCallback<Set<String>>) e -> {
            e.scan(opts).forEachRemaining(r -> res.add(new String(r)));
            return res;
        });
    }

    /**
     * 在限定范围内进行原子安全自减操作，自减成功时返回自减后的值，自减失败返回null
     * @param key   要操作的key
     * @param step  自减不长
     * @param min   允许自减后的最小值
     * @return      自减结果或失败后的null
     */
    public Long decrementAndGet(String key, int step, int min) {
        RedisScript<Long> script = RedisScript.of(new ClassPathResource("/lua/decrementAndGet.lua"), Long.class);
        return redisTemplate.execute(script, Collections.singletonList(key), step, min);
    }
}
