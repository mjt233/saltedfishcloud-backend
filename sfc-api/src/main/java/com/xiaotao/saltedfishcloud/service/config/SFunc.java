package com.xiaotao.saltedfishcloud.service.config;

import java.io.Serializable;

/**
 * 序列化lambda函数，用于通过lambda表达式反推对应类和字段，实现见{@link com.xiaotao.saltedfishcloud.utils.PropertyUtils#parseLambdaConfigKey(SFunc)}
 * @param <T>
 * @param <R>
 */
@FunctionalInterface
public interface SFunc<T,R> extends Serializable {
    R apply(T t);
}
