package com.xiaotao.saltedfishcloud.utils;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.function.Consumer;

public class PoolUtils {
    /**
     * 基于apache-common-pool2快速创建一个轻量级的对象池
     * @param objectFactory 对象工厂
     */
    public static <T> ObjectPool<T> createObjectPool(PooledObjectFactory<T> objectFactory) {
        return createObjectPool(objectFactory, config -> {});
    }

    /**
     * 基于apache-common-pool2创建一个对象池
     * @param objectFactory 对象工厂
     * @param config        对象池配置
     */
    public static <T> ObjectPool<T> createObjectPool(PooledObjectFactory<T> objectFactory, Consumer<GenericObjectPoolConfig<T>> config) {
        GenericObjectPoolConfig<T> defaultConfig = new GenericObjectPoolConfig<>();

        defaultConfig.setMinIdle(0);
        defaultConfig.setMaxIdle(8);
        defaultConfig.setMaxTotal(32);
        defaultConfig.setMaxWaitMillis(10000);
        // 10s一次空闲检测
        defaultConfig.setTimeBetweenEvictionRunsMillis(10000);
        config.accept(defaultConfig);
        return new GenericObjectPool<T>(objectFactory, defaultConfig);
    }
}
