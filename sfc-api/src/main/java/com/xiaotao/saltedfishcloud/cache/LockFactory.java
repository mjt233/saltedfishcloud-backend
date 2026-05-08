package com.xiaotao.saltedfishcloud.cache;

public interface LockFactory {
    DistributedLock getLock(String key);
}
