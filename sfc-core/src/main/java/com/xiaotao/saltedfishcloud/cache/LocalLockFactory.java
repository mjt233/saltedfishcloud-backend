package com.xiaotao.saltedfishcloud.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link LockFactory} 的本地实现。
 * <p>当 {@code sys.service.lock-provider=local} 时启用，仅在当前服务进程内提供可重入锁。</p>
 */
@Component
@ConditionalOnProperty(prefix = "sys.service", name = "lock-provider", havingValue = "local")
public class LocalLockFactory implements LockFactory {

    /**
     * 本地锁缓存。
     * <p>使用弱引用值，避免大量动态锁 key 长期占用堆内存。</p>
     */
    private final Cache<String, ReentrantLock> lockCache = Caffeine.newBuilder()
            .weakValues()
            .build();

    /**
     * 获取指定 key 的本地锁实例。
     *
     * @param lockKey 锁的标识 key
     * @return 当前进程内共享的可重入锁实例
     */
    @Override
    public Lock getLock(String lockKey) {
        return lockCache.get(lockKey, this::createLock);
    }

    /**
     * 创建新的本地锁实例。
     *
     * @param lockKey 锁的标识 key
     * @return 新建的本地可重入锁
     */
    private ReentrantLock createLock(String lockKey) {
        Objects.requireNonNull(lockKey, "lockKey");
        return new ReentrantLock();
    }
}

