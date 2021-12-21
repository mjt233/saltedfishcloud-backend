package com.xiaotao.saltedfishcloud.service.async.io.impl;

import com.xiaotao.saltedfishcloud.service.async.io.MessageWriter;

import java.util.ArrayDeque;

public class StringMessageWriter implements MessageWriter<String> {
    private final ArrayDeque<String> msgQueue;
    StringMessageWriter(ArrayDeque<String> msgQueue) {
        this.msgQueue = msgQueue;
    }
    @Override
    public synchronized void write(String msg) {
        msgQueue.addLast(msg);
    }
}
