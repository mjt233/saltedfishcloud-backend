package com.xiaotao.saltedfishcloud.compress.impl;

import com.xiaotao.saltedfishcloud.compress.filesystem.AbstractCompressFileSystem;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;

/**
 * @TODO 兼容多种编码（或用户指定）
 */
public class SequenceZipCompressFileSystem extends AbstractCompressFileSystem {
    private final InputStreamSource source;

    public SequenceZipCompressFileSystem(InputStreamSource source) {
        this.source = source;
    }

    @Override
    public ArchiveInputStream getArchiveInputStream() throws IOException, ArchiveException {
        return new ZipArchiveInputStream(source.getInputStream(), "GBK");
    }
}
