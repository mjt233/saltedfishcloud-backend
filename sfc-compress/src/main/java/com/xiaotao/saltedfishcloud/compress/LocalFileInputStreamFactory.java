package com.xiaotao.saltedfishcloud.compress;

import com.xiaotao.saltedfishcloud.compress.filesystem.InputStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalFileInputStreamFactory implements InputStreamFactory {
    private final Path path;

    public LocalFileInputStreamFactory(Path path) {
        this.path = path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }
}
