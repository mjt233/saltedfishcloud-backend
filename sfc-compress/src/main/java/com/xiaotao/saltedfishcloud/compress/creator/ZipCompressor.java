package com.xiaotao.saltedfishcloud.compress.creator;

import com.xiaotao.saltedfishcloud.compress.reader.CompressFile;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.OutputStream;

public class ZipCompressor extends AbstractCompressor {
    private final OutputStream outputStream;
    public ZipCompressor(OutputStream out) {
        this.outputStream = out;
    }

    @Override
    protected ArchiveOutputStream initArchiveOutputStream() {
        return new ZipArchiveOutputStream(outputStream);
    }

    @Override
    protected ArchiveEntry wrapEntry(CompressFile file) {
        ZipArchiveEntry ze = new ZipArchiveEntry(file.getPath());
        ze.setSize(file.getSize());
        return ze;
    }
}
