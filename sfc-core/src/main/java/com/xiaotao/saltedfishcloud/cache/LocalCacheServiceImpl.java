package com.xiaotao.saltedfishcloud.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link CacheService} 的本地内存实现。
 * <p>底层依赖 Spring 的 Caffeine CacheManager，额外补充了接口要求的 TTL、集合与扫描语义。</p>
 */
public class LocalCacheServiceImpl implements CacheService {

    /**
     * 本地缓存固定名称。
     */
    private static final String LOCAL_CACHE_NAME = "cache-service-local";

    /**
     * 永不过期时间戳。
     */
    private static final long NEVER_EXPIRE_AT = Long.MAX_VALUE;

    /**
     * 实际本地缓存。
     */
    private final Cache cache;

    /**
     * 已知key集合，用于支持scan语义。
     */
    private final Set<String> knownKeys = ConcurrentHashMap.newKeySet();

    /**
     * 通过缓存管理器创建本地缓存服务。
     *
     * @param cacheManager 缓存管理器
     */
    public LocalCacheServiceImpl(CacheManager cacheManager) {
        this.cache = cacheManager.getCache(LOCAL_CACHE_NAME);
        if (this.cache == null) {
            throw new IllegalStateException("Cache '" + LOCAL_CACHE_NAME + "' is not configured");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        LocalCacheValue cacheValue = getLocalValue(key);
        return cacheValue == null ? null : (T) cacheValue.value();
    }

    @Override
    public void set(String key, Object value) {
        cache.put(key, new LocalCacheValue(value, NEVER_EXPIRE_AT));
        knownKeys.add(key);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        cache.put(key, new LocalCacheValue(value, calculateExpireAt(ttl, unit)));
        knownKeys.add(key);
    }

    @Override
    public boolean setIfAbsent(String key, Object value, long ttl, TimeUnit unit) {
        synchronized (this) {
            LocalCacheValue existed = getLocalValue(key);
            if (existed != null) {
                return false;
            }
            set(key, value, ttl, unit);
            return true;
        }
    }

    @Override
    public Object getAndSet(String key, Object value) {
        synchronized (this) {
            LocalCacheValue oldValue = getLocalValue(key);
            set(key, value);
            return oldValue == null ? null : oldValue.value();
        }
    }

    @Override
    public boolean delete(String key) {
        LocalCacheValue existed = getLocalValue(key);
        cache.evict(key);
        knownKeys.remove(key);
        return existed != null;
    }

    @Override
    public long delete(Collection<String> keys) {
        long deletedCount = 0;
        for (String key : keys) {
            if (delete(key)) {
                deletedCount++;
            }
        }
        return deletedCount;
    }

    @Override
    public long sAdd(String key, Object... values) {
        synchronized (this) {
            AtomicLongHolder changedCount = new AtomicLongHolder();
            LocalCacheValue oldValue = getLocalValue(key);
            Set<Object> set = getOrCreateSet(oldValue);
            for (Object value : values) {
                if (set.add(value)) {
                    changedCount.increment();
                }
            }
            cache.put(key, new LocalCacheValue(set, inheritExpireAt(oldValue)));
            knownKeys.add(key);
            return changedCount.get();
        }
    }

    @Override
    public boolean sIsMember(String key, Object value) {
        LocalCacheValue cacheValue = getLocalValue(key);
        if (cacheValue == null || !(cacheValue.value() instanceof Set<?> set)) {
            return false;
        }
        return set.contains(value);
    }

    @Override
    public long sRemove(String key, Object... values) {
        synchronized (this) {
            LocalCacheValue oldValue = getLocalValue(key);
            if (oldValue == null || !(oldValue.value() instanceof Set<?> set)) {
                return 0;
            }
            AtomicLongHolder changedCount = new AtomicLongHolder();
            for (Object value : values) {
                if (set.remove(value)) {
                    changedCount.increment();
                }
            }
            cache.put(key, oldValue);
            return changedCount.get();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Set<T> sMembers(String key) {
        LocalCacheValue cacheValue = getLocalValue(key);
        if (cacheValue == null || !(cacheValue.value() instanceof Set<?> set)) {
            return null;
        }
        return (Set<T>) Set.copyOf(set);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> range(String key, long start, long end) {
        LocalCacheValue cacheValue = getLocalValue(key);
        if (cacheValue == null || !(cacheValue.value() instanceof List<?> list)) {
            return null;
        }
        int size = list.size();
        int from = normalizeStart(start, size);
        int to = normalizeEnd(end, size);
        if (from > to || from >= size) {
            return Collections.emptyList();
        }
        return (List<T>) new ArrayList<>(list.subList(from, to + 1));
    }

    @Override
    public boolean expire(String key, long ttl, TimeUnit unit) {
        synchronized (this) {
            LocalCacheValue oldValue = getLocalValue(key);
            if (oldValue == null) {
                return false;
            }
            cache.put(key, new LocalCacheValue(oldValue.value(), calculateExpireAt(ttl, unit)));
            return true;
        }
    }

    @Override
    public long getExpire(String key) {
        LocalCacheValue cacheValue = getLocalValue(key);
        if (cacheValue == null) {
            return -2;
        }
        if (cacheValue.expireAt() == NEVER_EXPIRE_AT) {
            return -1;
        }
        long seconds = Duration.ofMillis(cacheValue.expireAt() - System.currentTimeMillis()).toSeconds();
        return Math.max(seconds, 0);
    }

    @Override
    public boolean hasKey(String key) {
        return getLocalValue(key) != null;
    }

    @Override
    public Long decrementAndGet(String key, int step, int min) {
        synchronized (this) {
            LocalCacheValue oldValue = getLocalValue(key);
            if (oldValue == null || !(oldValue.value() instanceof Number number)) {
                return null;
            }
            long next = number.longValue() - step;
            if (next < min) {
                return null;
            }
            cache.put(key, new LocalCacheValue(next, inheritExpireAt(oldValue)));
            return next;
        }
    }

    @Override
    public Set<String> scanKeys(String pattern) {
        Pattern keyPattern = Pattern.compile(toRegexPattern(pattern));
        return knownKeys.stream()
                .filter(this::hasKey)
                .filter(key -> keyPattern.matcher(key).matches())
                .collect(Collectors.toSet());
    }

    /**
     * 获取本地缓存值，并在过期时惰性删除。
     *
     * @param key 缓存key
     * @return 本地缓存值
     */
    private LocalCacheValue getLocalValue(String key) {
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null || !(wrapper.get() instanceof LocalCacheValue value)) {
            return null;
        }
        if (value.expireAt() != NEVER_EXPIRE_AT && value.expireAt() <= System.currentTimeMillis()) {
            cache.evict(key);
            knownKeys.remove(key);
            return null;
        }
        return value;
    }

    /**
     * 计算绝对过期时间戳。
     *
     * @param ttl 过期时长
     * @param unit 时间单位
     * @return 绝对过期时间（毫秒时间戳）
     */
    private long calculateExpireAt(long ttl, TimeUnit unit) {
        if (ttl <= 0) {
            return System.currentTimeMillis();
        }
        long deltaMillis = unit.toMillis(ttl);
        return System.currentTimeMillis() + Math.max(deltaMillis, 1);
    }

    /**
     * 获取旧值的过期时间，若不存在则返回永不过期。
     *
     * @param oldValue 旧缓存值
     * @return 过期时间戳
     */
    private long inheritExpireAt(LocalCacheValue oldValue) {
        return oldValue == null ? NEVER_EXPIRE_AT : oldValue.expireAt();
    }

    /**
     * 获取或创建Set值。
     *
     * @param oldValue 旧缓存值
     * @return Set对象
     */
    private Set<Object> getOrCreateSet(LocalCacheValue oldValue) {
        if (oldValue != null && oldValue.value() instanceof Set<?> existSet) {
            @SuppressWarnings("unchecked")
            Set<Object> typed = (Set<Object>) existSet;
            return typed;
        }
        return ConcurrentHashMap.newKeySet();
    }

    /**
     * 将Redis风格的通配符转换为正则表达式。
     *
     * @param pattern Redis风格pattern
     * @return 正则表达式
     */
    private String toRegexPattern(String pattern) {
        String escaped = Pattern.quote(pattern);
        return escaped
                .replace("\\*", "*")
                .replace("\\?", "?")
                .replace("*", "\\E.*\\Q")
                .replace("?", "\\E.\\Q");
    }

    /**
     * 标准化起始索引。
     *
     * @param start 输入索引
     * @param size 列表长度
     * @return 标准化后的索引
     */
    private int normalizeStart(long start, int size) {
        if (start >= 0) {
            return (int) Math.min(start, size);
        }
        return Math.max(0, size + (int) start);
    }

    /**
     * 标准化结束索引（包含）。
     *
     * @param end 输入索引
     * @param size 列表长度
     * @return 标准化后的索引
     */
    private int normalizeEnd(long end, int size) {
        if (size == 0) {
            return -1;
        }
        if (end >= 0) {
            return (int) Math.min(end, size - 1);
        }
        return Math.max(-1, size + (int) end);
    }

    /**
     * 本地缓存值。
     *
     * @param value 缓存值
     * @param expireAt 过期时间戳（毫秒）
     */
    private record LocalCacheValue(Object value, long expireAt) {
    }

    /**
     * Caffeine 过期策略。
     */
    /**
     * 原子计数临时对象。
     */
    private static class AtomicLongHolder {

        /**
         * 计数值。
         */
        private long value;

        /**
         * 计数加一。
         */
        public void increment() {
            value++;
        }

        /**
         * 获取计数值。
         *
         * @return 当前计数
         */
        public long get() {
            return value;
        }
    }

}


