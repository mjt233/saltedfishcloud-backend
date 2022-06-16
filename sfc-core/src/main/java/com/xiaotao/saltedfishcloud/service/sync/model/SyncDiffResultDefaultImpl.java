package com.xiaotao.saltedfishcloud.service.sync.model;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Data
@Getter(AccessLevel.NONE)
public class SyncDiffResultDefaultImpl implements SyncDiffDetectResult{
    private List<String> deletedDirPaths;
    private List<String> newDirPaths;
    private List<FileInfo> newFiles;
    private List<FileInfo> deletedFiles;
    private List<FileChangeInfo> changeFiles;

    private <T> List<T> nonNullList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    @Override
    public List<String> getDeletedDirPaths() {
        return nonNullList(deletedDirPaths);
    }

    @Override
    public List<FileInfo> getDeletedFiles() {
        return nonNullList(deletedFiles);
    }

    @Override
    public List<FileInfo> getNewFiles() {
        return nonNullList(newFiles);
    }

    @Override
    public List<String> getNewDirPaths() {
        return nonNullList(newDirPaths);
    }

    @Override
    public List<FileChangeInfo> getChangeFiles() {
        return nonNullList(changeFiles);
    }
}
