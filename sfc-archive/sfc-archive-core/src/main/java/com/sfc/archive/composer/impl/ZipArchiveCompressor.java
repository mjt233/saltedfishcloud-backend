package com.sfc.archive.composer.impl;

import com.sfc.archive.comporessor.AbstractCompressor;
import com.sfc.archive.model.ArchiveFile;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Zip压缩器
 */
public class ZipArchiveCompressor extends AbstractCompressor {
    private final OutputStream outputStream;
    private final String encoding;
    public ZipArchiveCompressor(OutputStream out, String encoding) {
        this.encoding = encoding;
        this.outputStream = out;
    }

    @Override
    protected ArchiveOutputStream initArchiveOutputStream() {
        ZipArchiveOutputStream output = new ZipArchiveOutputStream(outputStream);
        output.setUseZip64(Zip64Mode.AsNeeded);
        output.setEncoding(encoding);
        return output;
    }

    @Override
    protected ArchiveEntry wrapEntry(ArchiveFile file) {
        ZipArchiveEntry ze = new ZipArchiveEntry(file.getPath());
        if (!ze.isDirectory()) {
            ze.setSize(file.getSize());
        }
        ze.setTime(System.currentTimeMillis());
        return ze;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (outputStream != null) {
            outputStream.close();
        }
    }
}
