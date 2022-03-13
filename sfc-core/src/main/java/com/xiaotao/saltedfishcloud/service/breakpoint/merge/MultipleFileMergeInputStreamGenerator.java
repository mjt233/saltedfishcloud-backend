package com.xiaotao.saltedfishcloud.service.breakpoint.merge;

import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class MultipleFileMergeInputStreamGenerator implements InputStreamGenerator {
    private final InputStreamSource[] sources;
    private int index = 0;
    public MultipleFileMergeInputStreamGenerator(InputStreamSource...sources) {
        this.sources = sources;
    }

    @Override
    public InputStream next() throws IOException {
        if (!hasNext()) return null;
        return sources[index++].getInputStream();
    }

    @Override
    public boolean hasNext() {
        return index < sources.length;
    }
}
