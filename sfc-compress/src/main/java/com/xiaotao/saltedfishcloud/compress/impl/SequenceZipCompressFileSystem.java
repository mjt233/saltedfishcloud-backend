package com.xiaotao.saltedfishcloud.compress.impl;

import com.xiaotao.saltedfishcloud.compress.filesystem.AbstractCompressFileSystem;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;

public class SequenceZipCompressFileSystem extends AbstractCompressFileSystem {
    private final InputStreamSource source;

    public SequenceZipCompressFileSystem(InputStreamSource source) {
        this.source = source;
    }

    @Override
    public ArchiveInputStream getArchiveInputStream() throws IOException, ArchiveException {
        return new ArchiveStreamFactory().createArchiveInputStream(
                ArchiveStreamFactory.ZIP,
                source.getInputStream(),
                "GBK");
    }
}
