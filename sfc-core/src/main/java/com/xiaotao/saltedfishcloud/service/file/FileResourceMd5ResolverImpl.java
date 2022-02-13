package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.springframework.stereotype.Component;

import java.nio.file.NoSuchFileException;

@Component
public class FileResourceMd5ResolverImpl implements FileResourceMd5Resolver {
    private final FileRecordService fileRecordService;

    public FileResourceMd5ResolverImpl(FileRecordService fileRecordService) {
        this.fileRecordService = fileRecordService;
    }

    @Override
    public String getResourceMd5(int uid, String path) {
        try {
            return fileRecordService.getFileInfo(uid, PathUtils.getParentPath(path), PathUtils.getLastNode(path)).getMd5();
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public boolean hasRef(String md5) {
        return !fileRecordService.getFileInfoByMd5(md5, 1).isEmpty();
    }
}
