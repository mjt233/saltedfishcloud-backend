package com.xiaotao.saltedfishcloud.model;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class DiskFileAttributes implements BasicFileAttributes {
    private FileInfo fileInfo;

    private DiskFileAttributes(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.from(fileInfo.getMtime(), TimeUnit.MILLISECONDS);
    }

    @Override
    public FileTime lastAccessTime() {
        return null;
    }

    @Override
    public FileTime creationTime() {
        return FileTime.from(Optional.ofNullable(fileInfo.getCtime()).orElse(fileInfo.getMtime()), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isRegularFile() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return fileInfo.isDir();
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        return fileInfo.getSize();
    }

    @Override
    public Object fileKey() {
        return fileInfo.getUid() + fileInfo.getNode() + fileInfo.getName();
    }

    public static DiskFileAttributes from(FileInfo fileInfo) {
        return new DiskFileAttributes(fileInfo);
    }
}
