package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 默认的临时存储服务实现，直接给原始直接存储操作器的路径操作添加临时目录前缀
 */
public class DefaultTempStoreService implements TempStoreService {
    private final DirectRawStoreHandler handler;
    private final String tempRootDir;

    public DefaultTempStoreService(DirectRawStoreHandler handler, String tempRootDir) {
        this.handler = handler;
        this.tempRootDir = tempRootDir;
    }

    @Override
    public void clean() throws IOException {
        handler.delete(tempRootDir);
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        return handler.isEmptyDirectory(StringUtils.appendPath(tempRootDir, path));
    }

    @Override
    public Resource getResource(String path) throws IOException {
        return handler.getResource(StringUtils.appendPath(tempRootDir, path));
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        return handler.listFiles(StringUtils.appendPath(tempRootDir, path));
    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        return handler.getFileInfo(StringUtils.appendPath(tempRootDir, path));
    }

    @Override
    public boolean delete(String path) throws IOException {
        return handler.delete(StringUtils.appendPath(tempRootDir, path));
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        return handler.mkdir(StringUtils.appendPath(tempRootDir, path));
    }

    @Override
    public long store(String path, InputStream inputStream) throws IOException {
        return handler.store(StringUtils.appendPath(tempRootDir, path), inputStream);
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        return handler.rename(StringUtils.appendPath(tempRootDir, path), newName);
    }

    @Override
    public boolean copy(String src, String dest) throws IOException {
        return handler.copy(
                StringUtils.appendPath(tempRootDir, src),
                StringUtils.appendPath(tempRootDir, dest)
        );
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        return handler.move(
                StringUtils.appendPath(tempRootDir, src),
                StringUtils.appendPath(tempRootDir, dest)
        );
    }
}
