package com.xiaotao.saltedfishcloud.cache;

import com.xiaotao.saltedfishcloud.config.cache.LocalCacheProperty;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Duration;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link CacheService} 的本地内存实现。
 * <p>通过进程内 ConcurrentHashMap 保存数据，并在读取时进行惰性过期清理，
 * 确保 key 级 TTL 不受 CacheManager 的全局过期策略约束。</p>
 */
@Slf4j
public class LocalCacheServiceImpl implements CacheService {

    /**
     * 持久化异步刷盘间隔（毫秒）。
     */
    private static final long PERSIST_FLUSH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30);

    /**
     * 永不过期时间戳。
     */
    private static final long NEVER_EXPIRE_AT = Long.MAX_VALUE;

    /**
     * 实际本地缓存存储。
     */
    private final Map<String, LocalCacheValue> localStore = new ConcurrentHashMap<>();

    /**
     * 默认缓存过期时间（毫秒）。
     */
    private final long defaultExpireMs;

    /**
     * 本地缓存最大数量。0 或负数表示不限制。
     */
    private final int maxCacheSize;

    /**
     * 是否启用本地缓存持久化。
     */
    private final boolean persistEnabled;

    /**
     * 本地缓存持久化文件路径。
     */
    private final Path persistFilePath;

    /**
     * 本地缓存持久化调度器。
     */
    private final ScheduledExecutorService persistScheduler;

    /**
     * 写入顺序队列，用于按最旧写入顺序淘汰。
     */
    private final Deque<CacheEntryRef> writeOrder = new ConcurrentLinkedDeque<>();

    /**
     * 条目版本序列号。
     */
    private final AtomicLong sequence = new AtomicLong();

    /**
     * 当前缓存是否存在尚未刷盘的变更。
     */
    private boolean persistDirty;

    /**
     * 构造本地缓存服务。
     *
     * @param localCacheProperty 本地缓存配置
     */
    public LocalCacheServiceImpl(LocalCacheProperty localCacheProperty) {
        Objects.requireNonNull(localCacheProperty, "localCacheProperty must not be null");
        this.defaultExpireMs = localCacheProperty.getDefaultExpireMs();
        this.maxCacheSize = localCacheProperty.getMaxCacheSize();
        this.persistEnabled = localCacheProperty.isPersistEnabled();
        this.persistFilePath = resolvePersistFilePath(localCacheProperty.getPersistFilePath());
        this.persistScheduler = persistEnabled ? createPersistScheduler() : null;
        if (persistEnabled) {
            loadPersistedData();
            startPersistScheduler();
        }
    }

    /**
     * Bean 销毁前执行一次缓存持久化，尽可能减少进程退出时的数据丢失。
     */
    @PreDestroy
    public void persistBeforeShutdown() {
        try {
            flushPersistedDataQuietly(true);
        } finally {
            shutdownPersistScheduler();
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
        synchronized (this) {
            putValue(key, value, defaultExpireAt());
        }
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        synchronized (this) {
            putValue(key, value, calculateExpireAt(ttl, unit));
        }
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
        synchronized (this) {
            boolean deleted = localStore.remove(key) != null;
            if (deleted) {
                markPersistDirty();
            }
            return deleted;
        }
    }

    @Override
    public long delete(Collection<String> keys) {
        synchronized (this) {
            long deletedCount = 0;
            for (String key : keys) {
                if (localStore.remove(key) != null) {
                    deletedCount++;
                }
            }
            if (deletedCount > 0) {
                markPersistDirty();
            }
            return deletedCount;
        }
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
            putValue(key, set, inheritExpireAt(oldValue));
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
            putValue(key, oldValue.value(), oldValue.expireAt());
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
            putValue(key, oldValue.value(), calculateExpireAt(ttl, unit));
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
            putValue(key, next, inheritExpireAt(oldValue));
            return next;
        }
    }

    @Override
    public Set<String> scanKeys(String pattern) {
        Pattern keyPattern = Pattern.compile(toRegexPattern(pattern));
        return localStore.keySet().stream()
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
        LocalCacheValue value = localStore.get(key);
        if (value == null) {
            return null;
        }
        if (isCacheExpired(value, System.currentTimeMillis())) {
            synchronized (this) {
                LocalCacheValue current = localStore.get(key);
                if (current != null && current.version() == value.version()
                        && isCacheExpired(current, System.currentTimeMillis())
                        && localStore.remove(key, current)) {
                    markPersistDirty();
                }
            }
            return null;
        }
        return value;
    }

    /**
     * 写入缓存并根据容量限制执行淘汰。
     *
     * @param key 缓存key
     * @param value 缓存值
     * @param expireAt 过期时间戳
     */
    private void putValue(String key, Object value, long expireAt) {
        long version = sequence.incrementAndGet();
        localStore.put(key, new LocalCacheValue(value, expireAt, version));
        writeOrder.offerLast(new CacheEntryRef(key, version));
        evictOverflow();
        markPersistDirty();
    }

    /**
     * 标记缓存存在待持久化变更。
     */
    private void markPersistDirty() {
        if (persistEnabled) {
            persistDirty = true;
        }
    }

    /**
     * 获取默认过期时间戳。
     *
     * @return 默认过期时间戳，配置为 0 或负数时表示永不过期
     */
    private long defaultExpireAt() {
        if (defaultExpireMs <= 0) {
            return NEVER_EXPIRE_AT;
        }
        return calculateExpireAt(defaultExpireMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 当缓存数量超过上限时，按写入顺序淘汰旧缓存。
     */
    private void evictOverflow() {
        if (maxCacheSize <= 0) {
            return;
        }
        int needEvict = localStore.size() - maxCacheSize;
        while (needEvict > 0) {
            CacheEntryRef oldest = writeOrder.pollFirst();
            if (oldest == null) {
                return;
            }
            LocalCacheValue current = localStore.get(oldest.key());
            if (current != null && current.version() == oldest.version() && localStore.remove(oldest.key(), current)) {
                needEvict--;
            }
        }
    }

    /**
     * 判断缓存是否已过期。
     *
     * @param cacheValue 缓存值
     * @param now 当前时间戳
     * @return true 表示已过期
     */
    private boolean isCacheExpired(LocalCacheValue cacheValue, long now) {
        return cacheValue.expireAt() != NEVER_EXPIRE_AT && cacheValue.expireAt() <= now;
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
     * 解析持久化文件路径。
     *
     * @param configuredPath 配置文件中的路径
     * @return 绝对路径
     */
    private Path resolvePersistFilePath(String configuredPath) {
        String path = configuredPath == null || configuredPath.isBlank() ? "./localcache.data" : configuredPath.trim();
        return Paths.get(path).toAbsolutePath().normalize();
    }

    /**
     * 创建本地缓存持久化调度器。
     *
     * @return 调度器
     */
    private ScheduledExecutorService createPersistScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "local-cache-persist-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 启动本地缓存异步持久化任务。
     */
    private void startPersistScheduler() {
        persistScheduler.scheduleWithFixedDelay(() -> flushPersistedDataQuietly(false),
                PERSIST_FLUSH_INTERVAL_MS,
                PERSIST_FLUSH_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭本地缓存持久化调度器。
     */
    private void shutdownPersistScheduler() {
        if (persistScheduler != null) {
            persistScheduler.shutdown();
        }
    }

    /**
     * 加载本地缓存持久化数据。
     */
    private void loadPersistedData() {
        if (Files.notExists(persistFilePath)) {
            return;
        }
        try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(persistFilePath))) {
            Object snapshotObject = inputStream.readObject();
            if (!(snapshotObject instanceof PersistedCacheSnapshot(List<PersistedCacheEntry> entries, long persistSequence))) {
                log.warn("本地缓存持久化文件格式无效，已跳过加载: {}", persistFilePath);
                return;
            }
            log.info("加载本地缓存持久化文件... 位置: {}", persistFilePath);
            synchronized (this) {
                localStore.clear();
                writeOrder.clear();
                long now = System.currentTimeMillis();
                long maxVersion = 0;
                for (PersistedCacheEntry entry : entries) {
                    if (entry == null || entry.key() == null || isExpired(entry.expireAt(), now)) {
                        continue;
                    }
                    LocalCacheValue cacheValue = new LocalCacheValue(entry.value(), entry.expireAt(), entry.version());
                    localStore.put(entry.key(), cacheValue);
                    writeOrder.offerLast(new CacheEntryRef(entry.key(), entry.version()));
                    maxVersion = Math.max(maxVersion, entry.version());
                }
                sequence.set(Math.max(persistSequence, maxVersion));
                evictOverflow();
                log.info("本地缓存持久化文件加载完成，耗时: {}s", (System.currentTimeMillis() - now)/1000d);
            }
        } catch (Exception e) {
            log.error("加载本地缓存持久化文件失败，已忽略该文件: {}", persistFilePath, e);
        }
    }

    /**
     * 安静地保存持久化数据。
     *
     * @param force true 表示即使未检测到变更也强制刷盘
     */
    private synchronized void flushPersistedDataQuietly(boolean force) {
        if (!persistEnabled || (!force && !persistDirty)) {
            return;
        }
        try {
            savePersistedData();
            persistDirty = false;
        } catch (Exception e) {
            persistDirty = true;
            log.error("保存本地缓存持久化文件失败: {}", persistFilePath, e);
        }
    }

    /**
     * 将当前缓存快照写入持久化文件。
     *
     * @throws IOException IO 异常
     */
    private void savePersistedData() throws IOException {
        log.info("保存位置: {}, 正在持久化缓存数据...", persistFilePath);
        long now = System.currentTimeMillis();
        PersistedCacheSnapshot snapshot = buildPersistedSnapshot();
        Path parent = persistFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFilePath = persistFilePath.resolveSibling(persistFilePath.getFileName() + ".tmp");
        try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(tempFilePath))) {
            outputStream.writeObject(snapshot);
            outputStream.flush();
        }
        try {
            Files.move(tempFilePath, persistFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFilePath, persistFilePath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            log.info("缓存数据持久化完成! 耗时: {}s", (System.currentTimeMillis() - now)/1000d);
        }
    }

    /**
     * 构建用于持久化的缓存快照。
     *
     * @return 缓存快照
     */
    private PersistedCacheSnapshot buildPersistedSnapshot() {
        List<PersistedCacheEntry> entries = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (CacheEntryRef entryRef : writeOrder) {
            LocalCacheValue current = localStore.get(entryRef.key());
            if (current == null || current.version() != entryRef.version() || isCacheExpired(current, now)) {
                continue;
            }
            try {
                Object persistValue = normalizePersistValue(current.value());
                entries.add(new PersistedCacheEntry(entryRef.key(), persistValue, current.expireAt(), current.version()));
            } catch (NotSerializableException e) {
                log.warn("本地缓存键 [{}] 的值不支持持久化，已跳过该条目", entryRef.key(), e);
            }
        }
        return new PersistedCacheSnapshot(entries, sequence.get());
    }

    /**
     * 标准化缓存值，确保其可被序列化。
     *
     * @param value 原始缓存值
     * @return 可持久化的缓存值
     * @throws NotSerializableException 值不可序列化时抛出
     */
    private Object normalizePersistValue(Object value) throws NotSerializableException {
        if (value == null) {
            return null;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>(set.size());
            for (Object item : set) {
                copy.add(normalizePersistValue(item));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(normalizePersistValue(item));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(normalizePersistValue(entry.getKey()), normalizePersistValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof Serializable) {
            return value;
        }
        throw new NotSerializableException(value.getClass().getName());
    }

    /**
     * 判断绝对时间戳是否已过期。
     *
     * @param expireAt 过期时间戳
     * @param now 当前时间戳
     * @return true 表示已过期
     */
    private boolean isExpired(long expireAt, long now) {
        return expireAt != NEVER_EXPIRE_AT && expireAt <= now;
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
     * @param version 缓存写入版本号
     */
    private record LocalCacheValue(Object value, long expireAt, long version) {
    }

    /**
     * 缓存条目引用。
     *
     * @param key 缓存key
     * @param version 写入版本号
     */
    private record CacheEntryRef(String key, long version) {
    }

    /**
     * 本地缓存持久化快照。
     *
     * @param entries 持久化条目
     * @param sequence 当前版本序列号
     */
    private record PersistedCacheSnapshot(List<PersistedCacheEntry> entries, long sequence) implements Serializable {
    }

    /**
     * 本地缓存持久化条目。
     *
     * @param key 缓存key
     * @param value 缓存值
     * @param expireAt 过期时间戳（毫秒）
     * @param version 缓存写入版本号
     */
    private record PersistedCacheEntry(String key, Object value, long expireAt, long version) implements Serializable {
    }

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


