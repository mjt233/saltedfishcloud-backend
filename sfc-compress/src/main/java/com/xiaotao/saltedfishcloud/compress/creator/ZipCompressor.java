package com.xiaotao.saltedfishcloud.compress.creator;

import com.xiaotao.saltedfishcloud.compress.reader.CompressFile;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class ZipCompressor extends AbstractCompressor {
    private final OutputStream outputStream;
    public ZipCompressor(OutputStream out) {
        this.outputStream = out;
    }

    @Override
    protected ArchiveOutputStream initArchiveOutputStream() {
        ZipArchiveOutputStream output = new ZipArchiveOutputStream(outputStream);
        output.setUseZip64(Zip64Mode.AsNeeded);
        output.setEncoding(OSInfo.getOSDefaultEncoding());
        return output;
    }

    @Override
    protected ArchiveEntry wrapEntry(CompressFile file) {
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
