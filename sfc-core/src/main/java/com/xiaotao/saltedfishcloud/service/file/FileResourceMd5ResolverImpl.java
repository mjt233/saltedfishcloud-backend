package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

@Component
public class FileResourceMd5ResolverImpl implements FileResourceMd5Resolver {
    private final FileRecordService fileRecordService;

    /**
     * @TODO 循环依赖了
     */
    @Autowired
    @Lazy
    private DiskFileSystemProvider fileSystemProvider;

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


    @Override
    public Resource getResourceByMd5(String md5) throws IOException {
        return fileSystemProvider.getFileSystem().getResourceByMd5(md5);
    }
}
