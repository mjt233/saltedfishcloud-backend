package com.sfc.archive.composer.impl.zip;

import com.sfc.archive.composer.AbstractCompressor;
import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.ArchiveParam;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * Zip压缩器
 */
public class ZipArchiveCompressor extends AbstractCompressor {

    public ZipArchiveCompressor(ArchiveParam archiveParam, OutputStream originOutput) {
        super(archiveParam, originOutput);
    }

    @Override
    protected ArchiveOutputStream initArchiveOutputStream() {
        ZipArchiveOutputStream output = new ZipArchiveOutputStream(originOutput);
        output.setUseZip64(Zip64Mode.AsNeeded);
        output.setEncoding(archiveParam.getEncoding());
        return output;
    }

    @Override
    protected ArchiveEntry wrapEntry(ArchiveFile file) {
        ZipArchiveEntry ze = new ZipArchiveEntry(file.getPath());
        if (!ze.isDirectory()) {
            ze.setSize(file.getSize());
        }
        long mtime = Objects.requireNonNullElseGet(file.getMtime(), System::currentTimeMillis);
        long ctime = Objects.requireNonNullElseGet(file.getCtime(), System::currentTimeMillis);
        ze.setLastModifiedTime(FileTime.fromMillis(mtime));
        ze.setCreationTime(FileTime.fromMillis(ctime));
        return ze;
    }
}
