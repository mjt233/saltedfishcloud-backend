package com.xiaotao.saltedfishcloud.function;

import java.io.IOException;

public interface IOExceptionBiConsumer<T, U> {
    void accept(T t, U u) throws IOException;
}