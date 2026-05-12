package com.xiaotao.saltedfishcloud.cache;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;

/**
 * {@link LockFactory} 的 Redisson 实现。
 * <p>通过 {@link RedissonClient} 创建基于 Redis 的分布式锁实例。</p>
 * <p>当 {@code sys.service.lock-provider=redisson}（或未配置）时启用。</p>
 */
@Component
@ConditionalOnProperty(prefix = "sys.service", name = "lock-provider", havingValue = "redisson", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisLockFactory implements LockFactory {
    private final RedissonClient redissonClient;

    @Override
    public Lock getLock(String key) {
        return redissonClient.getLock(key);
    }
}
