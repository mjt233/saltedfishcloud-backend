package com.xiaotao.saltedfishcloud.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * {@link CacheService} 的 Redis 实现。
 * <p>底层委托 {@link RedisTemplate} 执行实际的 Redis 操作，屏蔽 Redis API 细节。</p>
 */
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements CacheService {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, ttl, unit);
    }

    @Override
    public boolean setIfAbsent(String key, Object value, long ttl, TimeUnit unit) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, ttl, unit);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public Object getAndSet(String key, Object value) {
        return redisTemplate.opsForValue().getAndSet(key, value);
    }

    @Override
    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public long delete(Collection<String> keys) {
        Long result = redisTemplate.delete(keys);
        return result == null ? 0 : result;
    }

    @Override
    public long sAdd(String key, Object... values) {
        Long result = redisTemplate.opsForSet().add(key, values);
        return result == null ? 0 : result;
    }

    @Override
    public boolean sIsMember(String key, Object value) {
        Boolean result = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public long sRemove(String key, Object... values) {
        Long result = redisTemplate.opsForSet().remove(key, values);
        return result == null ? 0 : result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Set<T> sMembers(String key) {
        return (Set<T>) redisTemplate.opsForSet().members(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> range(String key, long start, long end) {
        return (List<T>) redisTemplate.opsForList().range(key, start, end);
    }

    @Override
    public boolean expire(String key, long ttl, TimeUnit unit) {
        Boolean result = redisTemplate.expire(key, ttl, unit);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public long getExpire(String key) {
        Long result = redisTemplate.getExpire(key);
        return result == null ? -2 : result;
    }

    @Override
    public boolean hasKey(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public Long decrementAndGet(String key, int step, int min) {
        RedisScript<Long> script = RedisScript.of(
            new ClassPathResource("/lua/decrementAndGet.lua"), Long.class);
        return redisTemplate.execute(script, Collections.singletonList(key), step, min);
    }

    @Override
    public Set<String> scanKeys(String pattern) {
        ScanOptions opts = ScanOptions.scanOptions().match(pattern).count(1000).build();
        Set<String> res = new HashSet<>();
        return redisTemplate.execute((RedisCallback<Set<String>>) e -> {
            e.scan(opts).forEachRemaining(r -> res.add(new String(r)));
            return res;
        });
    }
}
