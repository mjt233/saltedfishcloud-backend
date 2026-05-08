package com.xiaotao.saltedfishcloud.cache;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface CacheService {
    // --- Value 操作 ---
    <T> T get(String key);
    void set(String key, Object value);
    void set(String key, Object value, long ttl, TimeUnit unit);
    boolean setIfAbsent(String key, Object value, long ttl, TimeUnit unit);
    Object getAndSet(String key, Object value);
    Object getAndSet(String key, Object value, long ttl, TimeUnit unit);
    boolean delete(String key);
    long delete(Collection<String> keys);

    // --- Set 操作 ---
    long sAdd(String key, Object... values);
    boolean sIsMember(String key, Object value);
    long sRemove(String key, Object... values);
    <T> Set<T> sMembers(String key);

    // --- List 操作 ---
    <T> List<T> range(String key, long start, long end);

    // --- TTL 管理 ---
    boolean expire(String key, long ttl, TimeUnit unit);
    long getExpire(String key);
    boolean hasKey(String key);

    // --- 原子操作 ---
    Long decrementAndGet(String key, int step, int min);

    // --- Key 扫描 ---
    Set<String> scanKeys(String pattern);
}
