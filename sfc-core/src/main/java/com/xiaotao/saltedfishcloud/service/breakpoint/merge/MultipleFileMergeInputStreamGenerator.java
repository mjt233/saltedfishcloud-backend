package com.xiaotao.saltedfishcloud.service.breakpoint.merge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class MultipleFileMergeInputStreamGenerator implements InputStreamGenerator {
    private final Path[] paths;
    private int index = 0;
    public MultipleFileMergeInputStreamGenerator(Path...paths) {
        this.paths = paths;
    }

    @Override
    public InputStream next() throws IOException {
        if (!hasNext()) return null;
        return Files.newInputStream(paths[index++]);
    }

    @Override
    public boolean hasNext() {
        return index < paths.length;
    }
}
