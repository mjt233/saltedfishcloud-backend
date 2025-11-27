package com.xiaotao.saltedfishcloud.helper;

import java.io.IOException;
import java.io.OutputStream;

public interface OutputStreamConsumer<T extends OutputStream> {
    void accept(T os) throws IOException;
}
