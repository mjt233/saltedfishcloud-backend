package com.xiaotao.saltedfishcloud.service.async.io;

public interface TaskMessageIOPair<T> {
    MessageReader<T> getReader();
    MessageWriter<T> getWriter();
}
