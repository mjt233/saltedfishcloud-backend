package com.xiaotao.saltedfishcloud.function;

import java.io.IOException;

@FunctionalInterface
public interface IOExceptionFunction<T, R> {
    R apply(T t) throws IOException;
}
