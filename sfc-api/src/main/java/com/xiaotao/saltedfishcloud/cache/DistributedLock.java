package com.xiaotao.saltedfishcloud.cache;

import java.util.concurrent.TimeUnit;

public interface DistributedLock {
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
    void lock();
    void unlock();
    boolean isLocked();
}
