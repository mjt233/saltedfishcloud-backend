package com.xiaotao.saltedfishcloud.cache;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁接口，提供可重入的分布式锁能力。
 * <p>通过 {@link LockFactory#getLock(String)} 获取锁实例。</p>
 */
public interface DistributedLock {

    /**
     * 尝试在指定等待时间内获取锁
     *
     * @param waitTime  最大等待时间
     * @param leaseTime 持有时间（超时后自动释放）
     * @param unit      时间单位
     * @return true 表示成功获取锁
     * @throws InterruptedException 等待过程中被中断
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * 阻塞获取锁（直到获取成功）
     */
    void lock();

    /**
     * 释放当前持有的锁
     */
    void unlock();

    /**
     * 判断锁是否被任意线程持有
     *
     * @return true 表示锁正在被持有
     */
    boolean isLocked();
}
