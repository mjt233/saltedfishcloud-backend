package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 本地文件系统的直接原始操作类
 */
@Slf4j
public class LocalDirectRawStoreHandler implements DirectRawStoreHandler {
    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        if (exist(path)) {
            if (getFileInfo(path).isDir()) {
                throw new UnsupportedOperationException("不支持向文件夹写入数据");
            } else {
                delete(path);
            }
        } else {
            final String parent = PathUtils.getParentPath(path);
            mkdirs(parent);
        }

        return Files.newOutputStream(Paths.get(path));
    }

    @Override
    public Resource getResource(String path) throws IOException {
        final Path localPath = Paths.get(path);
        if (!Files.exists(localPath) || Files.isDirectory(localPath)) {
            return null;
        }
        return new PathResource(localPath);
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        final File[] files = new File(path).listFiles();
        if (files == null) {
            return false;
        }
        return files.length == 0;
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        final File root = new File(path);
        final File[] files = root.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<FileInfo> res = new ArrayList<>();
        for (File file : files) {
            res.add(new FileInfo(file));
        }
        return res;
    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        final Path path1 = Paths.get(path);
        if (!Files.exists(path1)) {
            return null;
        }
        return FileInfo.getFromResource(new PathResource(path1), 0, Files.isDirectory(path1) ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
    }

    @Override
    public boolean delete(String path) throws IOException {
        final Path p = Paths.get(path);
        if (Files.exists(p)) {
            return FileUtils.delete(p) > 0;
        } else {
            log.warn("[Direct Store]待删除的路径不存在：{}", path);
            return false;
        }
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        Files.createDirectories(Paths.get(path));
        return true;
    }

    @Override
    public long store(String path, InputStream inputStream) throws IOException {
        int cnt;
        final Path savePath = Paths.get(path);
        FileUtils.createParentDirectory(savePath);
        try(final OutputStream os = Files.newOutputStream(savePath)) {
            cnt = StreamUtils.copy(inputStream, os);
            inputStream.close();
        }
        return cnt;
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        final File file = new File(path);
        return file.renameTo(new File(PathUtils.getParentPath(path), newName));
    }

    @Override
    public boolean copy(String src, String dest) throws IOException {
        Files.copy(Paths.get(src), Paths.get(dest), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        return new File(src).renameTo(new File(dest));
    }

    @Override
    public boolean exist(String path) {
        return Files.exists(Paths.get(path));
    }
}
