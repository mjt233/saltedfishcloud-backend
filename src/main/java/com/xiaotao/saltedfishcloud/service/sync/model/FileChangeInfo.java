package com.xiaotao.saltedfishcloud.service.sync.model;

import com.xiaotao.saltedfishcloud.po.file.FileInfo;

public class FileChangeInfo {
    public FileInfo oldFile;
    public FileInfo newFile;

    public FileChangeInfo(FileInfo oldFile, FileInfo newFile) {
        this.oldFile = oldFile;
        this.newFile = newFile;
    }
}
