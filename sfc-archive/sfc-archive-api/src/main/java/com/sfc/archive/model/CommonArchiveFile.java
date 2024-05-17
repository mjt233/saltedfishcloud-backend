package com.sfc.archive.model;

import com.sfc.constant.RejectRegex;
import org.apache.commons.compress.archivers.ArchiveEntry;

import java.util.Date;
import java.util.regex.Pattern;

/**
 * 通用压缩文件信息
 */
public class CommonArchiveFile extends ArchiveFile {

    private final static Pattern pattern = Pattern.compile(RejectRegex.PATH);
    private final ArchiveEntry entry;
    private String path;
    public CommonArchiveFile(ArchiveEntry entry) {
        this.entry = entry;
        Date lastModifiedDate = entry.getLastModifiedDate();
        if (lastModifiedDate != null) {
            setCtime(lastModifiedDate.getTime());
        }
    }

    public long getSize() {
        return entry.getSize();
    }

    public String getPath() {
        if (this.path != null) return this.path;
        this.path = entry.getName();
        if (pattern.matcher(this.path).find() || this.path.indexOf('?') != -1) {
            throw new IllegalArgumentException("路径不合法" + this.path);
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
