package com.xiaotao.saltedfishcloud.cache;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 统一缓存服务接口，封装通用的 KV 缓存操作。
 * <p>提供 Value 读写、Set 集合操作、List 操作、TTL 管理、原子操作和 Key 扫描能力，
 * 屏蔽底层缓存实现细节，便于后续切换缓存实现（如从 Redis 切换到其他缓存中间件）。</p>
 */
public interface CacheService {

    // ==================== Value 操作 ====================

    /**
     * 获取缓存值
     *
     * @param key 缓存 key
     * @param <T> 返回值类型，调用方自行确保类型匹配
     * @return 缓存值，key 不存在时返回 null
     */
    <T> T get(String key);

    /**
     * 设置缓存值（无过期时间）
     *
     * @param key   缓存 key
     * @param value 缓存值
     */
    void set(String key, Object value);

    /**
     * 设置缓存值（带过期时间）
     *
     * @param key  缓存 key
     * @param value 缓存值
     * @param ttl   过期时间
     * @param unit  时间单位
     */
    void set(String key, Object value, long ttl, TimeUnit unit);

    /**
     * 仅当 key 不存在时设置缓存值（CAS 语义）
     *
     * @param key   缓存 key
     * @param value 缓存值
     * @param ttl   过期时间
     * @param unit  时间单位
     * @return true 表示设置成功（key 之前不存在），false 表示 key 已存在
     */
    boolean setIfAbsent(String key, Object value, long ttl, TimeUnit unit);

    /**
     * 原子地获取旧值并设置新值
     *
     * @param key   缓存 key
     * @param value 新值
     * @return key 之前的旧值，key 不存在时返回 null
     */
    Object getAndSet(String key, Object value);

    /**
     * 删除单个缓存 key
     *
     * @param key 缓存 key
     * @return true 表示成功删除，false 表示 key 不存在
     */
    boolean delete(String key);

    /**
     * 批量删除缓存 key
     *
     * @param keys 待删除的 key 集合
     * return 成功删除的 key 数量
     */
    long delete(Collection<String> keys);

    // ==================== Set 操作 ====================

    /**
     * 向 Set 集合中添加元素
     *
     * @param key    Set 的 key
     * @param values 待添加的元素
     * @return 成功添加的元素数量（不包括已存在的元素）
     */
    long sAdd(String key, Object... values);

    /**
     * 判断元素是否存在于 Set 集合中
     *
     * @param key   Set 的 key
     * @param value 待判断的元素
     * @return true 表示元素存在
     */
    boolean sIsMember(String key, Object value);

    /**
     * 从 Set 集合中移除元素
     *
     * @param key    Set 的 key
     * @param values 待移除的元素
     * @return 成功移除的元素数量
     */
    long sRemove(String key, Object... values);

    /**
     * 获取 Set 集合中的所有元素
     *
     * @param key Set 的 key
     * @param <T> 元素类型，调用方自行确保类型匹配
     * @return 元素集合，key 不存在时返回 null
     */
    <T> Set<T> sMembers(String key);

    // ==================== List 操作 ====================

    /**
     * 获取 List 中指定范围的元素
     *
     * @param key   List 的 key
     * @param start 起始索引（0 表示第一个元素，-1 表示最后一个元素）
     * @param end   结束索引（包含）
     * @param <T>   元素类型，调用方自行确保类型匹配
     * @return 元素列表，key 不存在时返回 null
     */
    <T> List<T> range(String key, long start, long end);

    // ==================== TTL 管理 ====================

    /**
     * 为 key 设置过期时间
     *
     * @param key 缓存 key
     * @param ttl 过期时间
     * @param unit 时间单位
     * @return true 表示设置成功
     */
    boolean expire(String key, long ttl, TimeUnit unit);

    /**
     * 获取 key 的剩余过期时间
     *
     * @param key 缓存 key
     * @return 剩余秒数；-1 表示永不过期；-2 表示 key 不存在
     */
    long getExpire(String key);

    /**
     * 判断 key 是否存在
     *
     * @param key 缓存 key
     * @return true 表示 key 存在
     */
    boolean hasKey(String key);

    // ==================== 原子操作 ====================

    /**
     * 原子自减操作，自减到 min 后不再继续
     *
     * @param key  缓存 key（值须为数字）
     * @param step 自减步长
     * @param min  最小值下限
     * @return 自减后的值；当 key 不存在或自减后低于 min 时返回 null
     */
    Long decrementAndGet(String key, int step, int min);

    // ==================== Key 扫描 ====================

    /**
     * 基于 SCAN 命令扫描匹配的 key（非阻塞，生产环境安全）
     *
     * @param pattern key 匹配模式（如 "sfc:token:*"）
     * @return 匹配的 key 集合
     */
    Set<String> scanKeys(String pattern);
}
