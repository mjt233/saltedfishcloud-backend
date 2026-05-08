package com.xiaotao.saltedfishcloud.cache;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisLockFactory implements LockFactory {
    private final RedissonClient redissonClient;

    @Override
    public DistributedLock getLock(String key) {
        return new RedisDistributedLock(redissonClient.getLock(key));
    }
}
