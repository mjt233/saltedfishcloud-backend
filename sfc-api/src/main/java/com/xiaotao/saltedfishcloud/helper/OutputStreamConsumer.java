package com.xiaotao.saltedfishcloud.helper;

import com.xiaotao.saltedfishcloud.utils.StreamCopyResult;

import java.io.IOException;
import java.io.OutputStream;

@FunctionalInterface
public interface OutputStreamConsumer<T extends OutputStream> {
    StreamCopyResult accept(T os) throws IOException;
}
