package com.xiaotao.saltedfishcloud.compress.reader.impl;

import com.xiaotao.saltedfishcloud.compress.reader.CompressFile;
import com.xiaotao.saltedfishcloud.compress.utils.CharacterUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;

public class CompressFileImpl extends CompressFile {
    private final ArchiveEntry zipEntry;
    private String path;
    public CompressFileImpl(ArchiveEntry entry) {
        this.zipEntry = entry;
    }

    public long getSize() {
        return zipEntry.getSize();
    }

    public String getPath() {
        if (this.path != null) return this.path;
        this.path = zipEntry.getName();
        if (CharacterUtils.isMessyCode(this.path)) {
            throw new UnsupportedOperationException("Just support GBK charset because I don't how to compatible with different charset :( ");
        }
        return this.path;
    }

    @Override
    public String toString() {
        return "ZipCompressFile{" +
                "name=" + getName() +
                '}';
    }
}
