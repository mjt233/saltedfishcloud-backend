package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.function.IOExceptionRunnable;
import com.xiaotao.saltedfishcloud.function.IOExceptionSupplier;
import lombok.experimental.UtilityClass;
import com.xiaotao.saltedfishcloud.cache.DistributedLock;
import com.xiaotao.saltedfishcloud.cache.LockFactory;

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
        LockFactory lockFactory = SpringContextUtils.getContext().getBean(LockFactory.class);
        DistributedLock lock = lockFactory.getLock(lockKey);
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
