package com.xiaotao.saltedfishcloud.compress.impl;

import com.xiaotao.saltedfishcloud.compress.filesystem.CompressFile;
import org.apache.commons.compress.archivers.ArchiveEntry;


public class CompressFileImpl implements CompressFile {
    private final ArchiveEntry zipEntry;
    private String path;
    private String name;
    public CompressFileImpl(ArchiveEntry entry) {
        this.zipEntry = entry;
    }

    public String getName() {
        if (this.name != null) return this.name;
        String path = getPath();
        int pos = path.lastIndexOf('/');
        if (pos == -1) {
            this.name = path;
        } else if (pos == path.length() - 1){
            int p2 = path.lastIndexOf('/', path.length() - 2);
            if (p2 == -1) {
                this.name = path.substring(0, path.length() - 1);
            } else {
                this.name = path.substring(pos + 1, path.length() - 1);
            }
        } else {
            this.name = path.substring(pos + 1);
        }
        return this.name;
    }

    public boolean isDirectory() {
        return zipEntry.isDirectory();
    }

    public long getSize() {
        return zipEntry.getSize();
    }

    public String getPath() {
        if (this.path != null) return this.path;
        this.path = zipEntry.getName();
        return this.path;
    }

    @Override
    public String toString() {
        return "ZipCompressFile{" +
                "name=" + getName() +
                '}';
    }
}
