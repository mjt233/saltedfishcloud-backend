package com.xiaotao.saltedfishcloud.function;

import java.io.IOException;

@FunctionalInterface
public interface IOExceptionRunnable {
    void run() throws IOException;
}
