package com.xiaotao.saltedfishcloud.utils.compress.reader.impl;

import com.xiaotao.saltedfishcloud.utils.compress.reader.CompressFile;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.validator.RejectRegex;
import org.apache.commons.compress.archivers.ArchiveEntry;

import java.util.regex.Pattern;

public class CompressFileImpl extends CompressFile {

    private final static Pattern pattern = Pattern.compile(RejectRegex.PATH);
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
        if (pattern.matcher(this.path).find() || this.path.indexOf('?') != -1) {
            throw new JsonException(CommonError.NOT_ALLOW_PATH);
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
