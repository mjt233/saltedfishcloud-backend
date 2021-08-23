package com.xiaotao.saltedfishcloud.service.async.io;

public interface MessageWriter<T> {
    void write(T msg);
}
