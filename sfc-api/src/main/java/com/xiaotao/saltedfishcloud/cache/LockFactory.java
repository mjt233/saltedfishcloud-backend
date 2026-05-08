package com.xiaotao.saltedfishcloud.cache;

/**
 * 分布式锁工厂，根据 key 创建 {@link DistributedLock} 实例。
 */
public interface LockFactory {

    /**
     * 获取指定 key 的分布式锁实例
     *
     * @param lockKey 锁的标识 key
     * @return 分布式锁实例
     */
    DistributedLock getLock(String lockKey);
}
