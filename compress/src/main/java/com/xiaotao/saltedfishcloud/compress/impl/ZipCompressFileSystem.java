package com.xiaotao.saltedfishcloud.compress.impl;

import com.xiaotao.saltedfishcloud.compress.filesystem.AbstractCompressFileSystem;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;

public class ZipCompressFileSystem extends AbstractCompressFileSystem {
    private final InputStreamSource source;

    public ZipCompressFileSystem(InputStreamSource source) {
        this.source = source;
    }

    @Override
    public ArchiveInputStream getArchiveInputStream() throws IOException {
        return new ZipArchiveInputStream(source.getInputStream(), "GBK");
    }
}
