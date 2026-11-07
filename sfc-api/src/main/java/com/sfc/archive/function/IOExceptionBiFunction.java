package com.sfc.archive.function;

import java.io.IOException;

/**
 * 支持抛出 {@link IOException} 的双参数函数式接口。
 *
 * @param <T> 第一个参数类型
 * @param <U> 第二个参数类型
 * @param <R> 返回值类型
 */
@FunctionalInterface
public interface IOExceptionBiFunction<T, U, R> {
    /**
     * 执行函数逻辑。
     *
     * @param t 第一个参数
     * @param u 第二个参数
     * @return 返回值
     * @throws IOException 执行失败
     */
    R apply(T t, U u) throws IOException;
}

