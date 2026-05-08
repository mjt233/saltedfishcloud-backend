package com.xiaotao.saltedfishcloud.cache;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * {@link LockFactory} 的 Redisson 实现。
 * <p>通过 {@link RedissonClient} 创建基于 Redis 的分布式锁实例。</p>
 */
@Component
@RequiredArgsConstructor
public class RedisLockFactory implements LockFactory {
    private final RedissonClient redissonClient;

    @Override
    public DistributedLock getLock(String key) {
        return new RedisDistributedLock(redissonClient.getLock(key));
    }
}
