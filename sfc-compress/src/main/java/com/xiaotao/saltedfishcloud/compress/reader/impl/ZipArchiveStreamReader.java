package com.xiaotao.saltedfishcloud.compress.reader.impl;

import com.xiaotao.saltedfishcloud.compress.reader.AbstractArchiveReader;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.core.io.Resource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZipArchiveStreamReader extends AbstractArchiveReader {
    private final Resource resource;

    public ZipArchiveStreamReader(Resource resource) {
        this.resource = resource;
    }

    @Override
    protected ArchiveInputStream getArchiveInputStream() throws IOException, ArchiveException {
        return new ZipArchiveInputStream(new BufferedInputStream(resource.getInputStream()), "GBK");
    }

    @Override
    public InputStream getInputStream(String fullPath) throws IOException, ArchiveException {
        return null;
    }

    @Override
    public void close() throws IOException {

    }
}
