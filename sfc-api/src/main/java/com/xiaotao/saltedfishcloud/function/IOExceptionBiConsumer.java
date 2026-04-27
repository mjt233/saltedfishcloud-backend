package com.xiaotao.saltedfishcloud.function;

import java.io.IOException;

@FunctionalInterface
public interface IOExceptionBiConsumer<T, U> {
    void accept(T t, U u) throws IOException;
}