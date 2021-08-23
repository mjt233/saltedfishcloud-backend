package com.xiaotao.saltedfishcloud.service.async.io.impl;

import com.xiaotao.saltedfishcloud.service.async.io.MessageReader;
import com.xiaotao.saltedfishcloud.service.async.io.MessageWriter;
import com.xiaotao.saltedfishcloud.service.async.io.TaskMessageIOPair;

import java.util.ArrayDeque;

public class StringMessageIOPair implements TaskMessageIOPair<String> {
    private final MessageReader<String> reader;
    private final MessageWriter<String> writer;
    public StringMessageIOPair() {
        ArrayDeque<String> messageQueue = new ArrayDeque<>();
        reader = new StringMessageReader(messageQueue);
        writer = new StringMessageWriter(messageQueue);
    }
    @Override
    public MessageReader<String> getReader() {
        return reader;
    }

    @Override
    public MessageWriter<String> getWriter() {
        return writer;
    }
}
