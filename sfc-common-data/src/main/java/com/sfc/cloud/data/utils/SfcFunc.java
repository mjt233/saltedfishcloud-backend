package com.sfc.cloud.data.utils;

import java.io.Serializable;

/**
 * 序列化lambda函数，用于通过lambda表达式反推对应类和字段，实现见{@link SfcClassUtils#parseGetterLambdaMetaData(SfcFunc)}
 * @param <T> 入参类型（通常是实体类）
 * @param <R> 返回值类型（通常是实体类的某个字段类型）
 */
@FunctionalInterface
public interface SfcFunc<T,R> extends Serializable {

    /**
     * 执行函数。
     *
     * @param t 入参
     * @return 函数执行结果
     */
    R apply(T t);
}
