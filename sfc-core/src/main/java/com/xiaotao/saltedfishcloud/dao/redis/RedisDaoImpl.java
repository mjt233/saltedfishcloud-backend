package com.xiaotao.saltedfishcloud.dao.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisDaoImpl implements RedisDao {
    private final RedisTemplate<String, Object> redisTemplate;
    @Override
    public Set<String> scanKeys(String pattern) {
        ScanOptions opts = ScanOptions.scanOptions().match(pattern).count(1000).build();
        Set<String> res = new HashSet<>();
        return redisTemplate.execute((RedisCallback<Set<String>>) e -> {
            e.scan(opts).forEachRemaining(r -> res.add(new String(r)));
            return res;
        });
    }

    @Override
    public Long decrementAndGet(String key, int step, int min) {
        RedisScript<Long> script = RedisScript.of(new ClassPathResource("/lua/decrementAndGet.lua"), Long.class);
        return redisTemplate.execute(script, Collections.singletonList(key), step, min);
    }
}
