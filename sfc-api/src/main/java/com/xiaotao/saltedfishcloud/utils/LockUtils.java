package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.function.IOExceptionRunnable;
import com.xiaotao.saltedfishcloud.function.IOExceptionSupplier;
import lombok.experimental.UtilityClass;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.io.IOException;

@UtilityClass
public class LockUtils {
    public static void execute(String lockKey, IOExceptionRunnable task) {
        execute(lockKey, () -> {
            task.run();
            return null;
        });
    }

    public static <T> T execute(String lockKey, IOExceptionSupplier<T> task) {
        RedissonClient redisson = SpringContextUtils.getContext().getBean(RedissonClient.class);
        RLock lock = redisson.getLock(lockKey);
        try {
            lock.lock();
            return task.get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
