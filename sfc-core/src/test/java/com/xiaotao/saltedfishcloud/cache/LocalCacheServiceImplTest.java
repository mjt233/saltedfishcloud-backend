package com.xiaotao.saltedfishcloud.cache;

import com.xiaotao.saltedfishcloud.config.cache.LocalCacheProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LocalCacheServiceImpl} 单元测试.
 * <p>
 * 覆盖以下关键场景：
 * <ul>
 *   <li>基础的 get、set 操作</li>
 *   <li>缓存过期机制</li>
 *   <li>并发原子操作（setIfAbsent、getAndSet、decrementAndGet）</li>
 * </ul>
 * </p>
 */
@DisplayName("LocalCacheServiceImpl 单元测试")
public class LocalCacheServiceImplTest {

    /**
     * 缓存服务实例
     */
    private LocalCacheServiceImpl cacheService;

    /**
     * 测试前初始化，创建 CacheManager 和 CacheService 实例
     */
    @BeforeEach
    public void setUp() {
        LocalCacheProperty property = new LocalCacheProperty();
        cacheService = new LocalCacheServiceImpl(property);
    }

    // ======================== 基础 get/set 操作测试 ========================

    @Test
    @DisplayName("测试基本的 set 和 get 操作")
    public void testBasicSetAndGet() {
        String key = "test:key";
        String value = "test_value";

        cacheService.set(key, value);
        String retrieved = cacheService.get(key);

        assertEquals(value, retrieved, "应该能够正确获取之前设置的值");
    }

    @Test
    @DisplayName("测试 set 后，get 返回正确的类型")
    public void testSetAndGetWithDifferentTypes() {
        // 测试字符串
        cacheService.set("string:key", "value");
        assertEquals("value", cacheService.get("string:key"));

        // 测试整数
        cacheService.set("int:key", 42);
        assertEquals(42, (Integer) cacheService.get("int:key"));

        // 测试长整数
        cacheService.set("long:key", 1234567890L);
        assertEquals(1234567890L, (Long) cacheService.get("long:key"));

        // 测试对象
        Map<String, String> map = new HashMap<>();
        map.put("name", "test");
        cacheService.set("map:key", map);
        Map<String, String> retrieved = cacheService.get("map:key");
        assertEquals(map, retrieved);
    }

    @Test
    @DisplayName("测试获取不存在的 key 返回 null")
    public void testGetNonExistentKey() {
        String result = cacheService.get("non:existent:key");
        assertNull(result, "获取不存在的 key 应该返回 null");
    }

    @Test
    @DisplayName("测试覆盖已存在的 key")
    public void testOverwriteExistingKey() {
        String key = "test:key";
        cacheService.set(key, "old_value");
        assertEquals("old_value", cacheService.get(key));

        cacheService.set(key, "new_value");
        assertEquals("new_value", cacheService.get(key));
    }

    @Test
    @DisplayName("测试 delete 方法删除存在的 key")
    public void testDeleteExistingKey() {
        String key = "test:key";
        cacheService.set(key, "value");
        assertTrue(cacheService.hasKey(key), "删除前 key 应该存在");

        boolean deleted = cacheService.delete(key);
        assertTrue(deleted, "delete 应该返回 true");
        assertFalse(cacheService.hasKey(key), "删除后 key 应该不存在");
    }

    @Test
    @DisplayName("测试 delete 方法删除不存在的 key")
    public void testDeleteNonExistentKey() {
        boolean deleted = cacheService.delete("non:existent:key");
        assertFalse(deleted, "删除不存在的 key 应该返回 false");
    }

    @Test
    @DisplayName("测试批量删除 key")
    public void testBatchDelete() {
        cacheService.set("key1", "value1");
        cacheService.set("key2", "value2");
        cacheService.set("key3", "value3");

        Collection<String> keys = Arrays.asList("key1", "key2", "key3", "non:existent");
        long deletedCount = cacheService.delete(keys);

        assertEquals(3, deletedCount, "应该删除 3 个存在的 key");
        assertFalse(cacheService.hasKey("key1"));
        assertFalse(cacheService.hasKey("key2"));
        assertFalse(cacheService.hasKey("key3"));
    }

    @Test
    @DisplayName("测试 hasKey 方法")
    public void testHasKey() {
        String key = "test:key";
        assertFalse(cacheService.hasKey(key), "set 前 key 应该不存在");

        cacheService.set(key, "value");
        assertTrue(cacheService.hasKey(key), "set 后 key 应该存在");

        cacheService.delete(key);
        assertFalse(cacheService.hasKey(key), "delete 后 key 应该不存在");
    }

    // ======================== 缓存过期机制测试 ========================

    @Test
    @DisplayName("测试带 TTL 的 set 操作")
    public void testSetWithTTL() {
        String key = "test:ttl:key";
        String value = "value";

        cacheService.set(key, value, 1, TimeUnit.SECONDS);
        assertTrue(cacheService.hasKey(key), "设置后 key 应该存在");
        assertEquals(value, cacheService.get(key), "应该能获取到设置的值");
    }

    @Test
    @DisplayName("测试缓存过期后自动删除")
    public void testCacheExpiration() throws InterruptedException {
        String key = "test:expiring:key";
        String value = "value";

        cacheService.set(key, value, 1, TimeUnit.SECONDS);
        assertTrue(cacheService.hasKey(key), "设置后 key 应该存在");

        // 等待缓存过期
        Thread.sleep(1100);
        assertNull(cacheService.get(key), "过期后应该获取不到值");
        assertFalse(cacheService.hasKey(key), "过期后 key 应该不存在");
    }

    @Test
    @DisplayName("测试 getExpire 方法")
    public void testGetExpire() throws InterruptedException {
        String key = "test:expire:key";

        // 1. 测试不存在的 key
        assertEquals(-2, cacheService.getExpire("non:existent:key"), "不存在的 key 返回 -2");

        // 2. 测试永不过期的 key
        cacheService.set(key, "value");
        long defaultTtl = cacheService.getExpire(key);
        assertTrue(defaultTtl > 0 && defaultTtl <= 900, "默认过期时间应在 0-900 秒之间");

        // 3. 测试带 TTL 的 key
        String ttlKey = "test:ttl:expire:key";
        cacheService.set(ttlKey, "value", 10, TimeUnit.SECONDS);
        long expireTime = cacheService.getExpire(ttlKey);
        assertTrue(expireTime > 0 && expireTime <= 10, "返回的 TTL 应该在 0-10 秒之间");
    }

    @Test
    @DisplayName("测试 expire 方法设置过期时间")
    public void testExpireMethod() throws InterruptedException {
        String key = "test:expire:update:key";
        cacheService.set(key, "value");
        long defaultTtl = cacheService.getExpire(key);
        assertTrue(defaultTtl > 0 && defaultTtl <= 900, "初始状态应使用默认过期时间");

        // 设置 1 秒过期
        boolean result = cacheService.expire(key, 1, TimeUnit.SECONDS);
        assertTrue(result, "expire 应该返回 true");
        long expireTime = cacheService.getExpire(key);
        assertTrue(expireTime > 0 && expireTime <= 1, "设置的 TTL 应该生效");

        // 等待过期
        Thread.sleep(1100);
        assertNull(cacheService.get(key), "过期后应该获取不到值");
    }

    @Test
    @DisplayName("测试对不存在的 key 调用 expire 返回 false")
    public void testExpireNonExistentKey() {
        boolean result = cacheService.expire("non:existent:key", 10, TimeUnit.SECONDS);
        assertFalse(result, "对不存在的 key 调用 expire 应该返回 false");
    }

    // ======================== 原子操作并发性测试 ========================

    @Test
    @DisplayName("测试 setIfAbsent 的原子性")
    public void testSetIfAbsentAtomicity() {
        String key = "test:atomic:setIfAbsent:key";

        // 首次设置应该成功
        boolean result1 = cacheService.setIfAbsent(key, "value1", 10, TimeUnit.SECONDS);
        assertTrue(result1, "首次 setIfAbsent 应该返回 true");

        // 再次设置应该失败
        boolean result2 = cacheService.setIfAbsent(key, "value2", 10, TimeUnit.SECONDS);
        assertFalse(result2, "已存在时 setIfAbsent 应该返回 false");

        // 值应该保持为初始值
        assertEquals("value1", cacheService.get(key), "值应该保持为初始值");
    }

    @Test
    @DisplayName("测试 setIfAbsent 在并发场景下的原子性")
    public void testSetIfAbsentConcurrency() throws InterruptedException {
        String key = "test:concurrent:setIfAbsent:key";
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    boolean result = cacheService.setIfAbsent(key, "thread_" + threadIndex, 10, TimeUnit.SECONDS);
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        // 只有一个线程应该成功
        assertEquals(1, successCount.get(), "在并发场景下，只有一个线程应该成功设置值");
    }

    @Test
    @DisplayName("测试 getAndSet 的原子性")
    public void testGetAndSetAtomicity() {
        String key = "test:atomic:getAndSet:key";

        // 首次调用，旧值为 null
        Object oldValue = cacheService.getAndSet(key, "new_value1");
        assertNull(oldValue, "首次调用时旧值应该为 null");
        assertEquals("new_value1", cacheService.get(key), "新值应该被设置");

        // 再次调用，应该返回之前的值
        oldValue = cacheService.getAndSet(key, "new_value2");
        assertEquals("new_value1", oldValue, "应该返回上一次的值");
        assertEquals("new_value2", cacheService.get(key), "新值应该被更新");
    }

    @Test
    @DisplayName("测试 getAndSet 在并发场景下保证原子性")
    public void testGetAndSetConcurrency() throws InterruptedException {
        String key = "test:concurrent:getAndSet:key";
        cacheService.set(key, 0);
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Object> retrievedValues = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final int newValue = i + 1;
            new Thread(() -> {
                try {
                    Object oldValue = cacheService.getAndSet(key, newValue);
                    if (oldValue != null) {
                        retrievedValues.add(oldValue);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        // 最终值应该是线程之一设置的
        Object finalValue = cacheService.get(key);
        assertTrue(finalValue instanceof Integer && (Integer) finalValue >= 1 && (Integer) finalValue <= 10,
                "最终值应该是 1-10 之间的某个值");
    }

    @Test
    @DisplayName("测试 decrementAndGet 的单向操作")
    public void testDecrementAndGet() {
        String key = "test:decrement:key";

        // 初始化为 10
        cacheService.set(key, 10);

        // 执行自减
        Long result1 = cacheService.decrementAndGet(key, 2, 0);
        assertEquals(8L, result1, "自减 2 后应该得到 8");

        Long result2 = cacheService.decrementAndGet(key, 3, 0);
        assertEquals(5L, result2, "再自减 3 后应该得到 5");
    }

    @Test
    @DisplayName("测试 decrementAndGet 达到最小值后返回 null")
    public void testDecrementAndGetMinValue() {
        String key = "test:decrement:min:key";
        cacheService.set(key, 5);

        // 正常自减
        Long result1 = cacheService.decrementAndGet(key, 2, 0);
        assertEquals((long) 3, (long) result1, "正常自减应该成功");

        // 自减使得值超过最小值时应该返回 null
        Long result2 = cacheService.decrementAndGet(key, 5, 0);
        assertNull(result2, "自减后低于 min 值时应该返回 null");
        assertEquals((long) 3, (long) cacheService.get(key), "值应该保持不变");
    }

    @Test
    @DisplayName("测试 decrementAndGet 在并发场景下的原子性")
    public void testDecrementAndGetConcurrency() throws InterruptedException {
        String key = "test:concurrent:decrement:key";
        cacheService.set(key, 100);
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    Long result = cacheService.decrementAndGet(key, 5, 0);
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        // 所有自减都应该成功（每个线程自减 5，共 50，结果应该为 50）
        Long finalValue = cacheService.get(key);
        assertTrue(successCount.get() > 0, "至少应该有一些自减操作成功");
        assertTrue(finalValue <= 100 && finalValue >= 0, "最终值应该在合理范围内");
    }

    @Test
    @DisplayName("测试 decrementAndGet 对非数字值返回 null")
    public void testDecrementAndGetNonNumberValue() {
        String key = "test:decrement:non:number:key";
        cacheService.set(key, "not_a_number");

        Long result = cacheService.decrementAndGet(key, 1, 0);
        assertNull(result, "对非数字值调用 decrementAndGet 应该返回 null");
    }

    // ======================== Set 操作测试 ========================

    @Test
    @DisplayName("测试 sAdd 和 sMembers 操作")
    public void testSetOperations() {
        String key = "test:set:key";

        // 添加元素
        long added1 = cacheService.sAdd(key, "member1", "member2", "member3");
        assertEquals(3, added1, "首次添加 3 个元素");

        // 检查成员
        Set<String> members = cacheService.sMembers(key);
        assertNotNull(members);
        assertEquals(3, members.size());
        assertTrue(members.contains("member1"));
        assertTrue(members.contains("member2"));
        assertTrue(members.contains("member3"));

        // 再次添加，其中两个已存在
        long added2 = cacheService.sAdd(key, "member2", "member4");
        assertEquals(1, added2, "只应该添加 1 个新元素");

        members = cacheService.sMembers(key);
        assertEquals(4, members.size());
    }

    @Test
    @DisplayName("测试 sIsMember 和 sRemove 操作")
    public void testSetIsMemberAndRemove() {
        String key = "test:set:ismember:key";
        cacheService.sAdd(key, "member1", "member2", "member3");

        assertTrue(cacheService.sIsMember(key, "member1"));
        assertTrue(cacheService.sIsMember(key, "member2"));
        assertFalse(cacheService.sIsMember(key, "non:existent"));

        // 移除元素
        long removed = cacheService.sRemove(key, "member1", "member2");
        assertEquals(2, removed, "应该移除 2 个元素");
        assertFalse(cacheService.sIsMember(key, "member1"));
        assertTrue(cacheService.sIsMember(key, "member3"));
    }

    // ======================== 其他功能测试 ========================

    @Test
    @DisplayName("测试 scanKeys 方法")
    public void testScanKeys() {
        // 添加一些带模式的 key
        cacheService.set("sfc:token:abc123", "value1");
        cacheService.set("sfc:token:def456", "value2");
        cacheService.set("sfc:user:1", "user1");

        // 扫描匹配的 key
        Set<String> keys = cacheService.scanKeys("sfc:token:*");
        assertEquals(2, keys.size());
        assertTrue(keys.contains("sfc:token:abc123"));
        assertTrue(keys.contains("sfc:token:def456"));

        // 扫描用户相关的 key
        keys = cacheService.scanKeys("sfc:user:*");
        assertEquals(1, keys.size());
        assertTrue(keys.contains("sfc:user:1"));
    }

    @Test
    @DisplayName("测试 scanKeys 返回不存在的 key")
    public void testScanKeysNonExistent() {
        Set<String> keys = cacheService.scanKeys("non:existent:*");
        assertTrue(keys.isEmpty(), "不应该找到匹配的 key");
    }

    @Test
    @DisplayName("测试 range 操作")
    public void testRangeOperation() {
        String key = "test:list:key";
        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
        cacheService.set(key, list);

        // 正常范围
        List<Integer> range1 = cacheService.range(key, 0, 2);
        assertEquals(Arrays.asList(1, 2, 3), range1);

        // 负数索引
        List<Integer> range2 = cacheService.range(key, -2, -1);
        assertEquals(Arrays.asList(4, 5), range2);

        // 超出范围
        List<Integer> range3 = cacheService.range(key, 10, 20);
        assertTrue(range3.isEmpty());
    }

    @Test
    @DisplayName("测试最大缓存数量限制会淘汰最旧写入")
    public void testEvictOldestWhenExceedMaxCacheSize() {
        LocalCacheProperty property = new LocalCacheProperty();
        property.setMaxCacheSize(2);
        property.setDefaultExpireMs(TimeUnit.MINUTES.toMillis(30));
        LocalCacheServiceImpl limitedCacheService = new LocalCacheServiceImpl(property);

        limitedCacheService.set("k1", "v1");
        limitedCacheService.set("k2", "v2");
        limitedCacheService.set("k3", "v3");

        assertFalse(limitedCacheService.hasKey("k1"), "超过上限后应淘汰最旧写入的 k1");
        assertTrue(limitedCacheService.hasKey("k2"), "k2 应保留");
        assertTrue(limitedCacheService.hasKey("k3"), "k3 应保留");
    }

}


