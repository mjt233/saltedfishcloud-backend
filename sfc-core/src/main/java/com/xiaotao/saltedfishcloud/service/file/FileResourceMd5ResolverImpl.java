package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

@Component
public class FileResourceMd5ResolverImpl implements FileResourceMd5Resolver {
    private final FileRecordService fileRecordService;

    @Autowired
    @Lazy
    private DiskFileSystemManager fileSystemProvider;

    public FileResourceMd5ResolverImpl(FileRecordService fileRecordService) {
        this.fileRecordService = fileRecordService;
    }

    @Override
    public String getResourceMd5(long uid, String path) {
        FileInfo fileInfo = fileRecordService.getFileInfo(uid, PathUtils.getParentPath(path), PathUtils.getLastNode(path));
        if (fileInfo == null) {
            return null;
        }
        return fileInfo.getMd5();
    }

    @Override
    public boolean hasRef(String md5) {
        return !fileRecordService.getFileInfoByMd5(md5, 1).isEmpty();
    }


    @Override
    public Resource getResourceByMd5(String md5) throws IOException {
        return fileSystemProvider.getMainFileSystem().getResourceByMd5(md5);
    }
}
