package com.xiaotao.saltedfishcloud.cache;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RedisDistributedLock implements DistributedLock {
    private final RLock rLock;

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return rLock.tryLock(waitTime, leaseTime, unit);
    }

    @Override
    public void lock() {
        rLock.lock();
    }

    @Override
    public void unlock() {
        rLock.unlock();
    }

    @Override
    public boolean isLocked() {
        return rLock.isLocked();
    }
}
