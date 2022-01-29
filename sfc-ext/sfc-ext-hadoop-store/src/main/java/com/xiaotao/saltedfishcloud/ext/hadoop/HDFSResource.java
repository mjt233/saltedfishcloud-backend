package com.xiaotao.saltedfishcloud.ext.hadoop;

import lombok.RequiredArgsConstructor;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
public class HDFSResource extends AbstractResource {
    private final FileSystem fs;
    private final Path path;

    @Override
    public long contentLength() throws IOException {
        return fs.getFileStatus(path).getLen();
    }

    @Override
    public long lastModified() throws IOException {
        return fs.getFileStatus(path).getModificationTime();
    }

    @Override
    public String getFilename() {
        return path.getName();
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public String getDescription() {
        return path.toString();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return fs.open(path);
    }
}
