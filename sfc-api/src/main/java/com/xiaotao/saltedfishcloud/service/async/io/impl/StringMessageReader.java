package com.xiaotao.saltedfishcloud.service.async.io.impl;

import com.xiaotao.saltedfishcloud.service.async.io.MessageReader;

import java.util.ArrayDeque;

public class StringMessageReader implements MessageReader<String> {
    private final ArrayDeque<String> msgQueue;
    StringMessageReader(ArrayDeque<String> msgQueue) {
        this.msgQueue = msgQueue;
    }
    @Override
    public synchronized String read() {
        if (msgQueue.size() == 0) {
            return null;
        } else {
            String msg = msgQueue.getFirst();
            msgQueue.removeFirst();
            return msg;
        }
    }
}
