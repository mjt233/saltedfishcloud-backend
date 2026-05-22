package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StreamUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 本地文件系统的直接原始操作类
 */
@Slf4j
public class LocalStorage implements Storage {
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
        return FileInfo.getFromResource(new PathResource(path1) {
            @Override
            public String getFilename() {
                if (path1.getFileName() == null) {
                    return "";
                } else {
                    return path1.getFileName().toString();
                }
            }
        }, 0L, Files.isDirectory(path1) ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
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
    public long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException {
        long cnt;
        final Path savePath = Paths.get(path);
        FileUtils.createParentDirectory(savePath);
        try (final OutputStream os = Files.newOutputStream(savePath)) {
            cnt = StreamUtils.copyStream(inputStream, os);
            inputStream.close();
            os.close();
            if (fileInfo.getMtime() != null) {
                Files.setLastModifiedTime(savePath, FileTime.fromMillis(fileInfo.getMtime()));
            }
        } catch (AccessDeniedException e) {
            throw new IOException("权限不足", e);
        }
        return cnt;
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        final File file = new File(path);
        return file.renameTo(new File(PathUtils.getParentPath(path), newName));
    }

    @Override
    public boolean copy(String src, String dest,@Nullable FileTransferItem item) throws IOException {
        Path srcPath = Paths.get(src);
        Path destPath = Paths.get(dest);
        FileTransferItem transferItem = item == null ? new FileTransferItem() : item;
        transferItem.setLoaded(0L);
        if (!Files.isDirectory(srcPath)) {
            transferItem.setTotal(Files.size(srcPath));
            try(InputStream is = Files.newInputStream(srcPath); OutputStream os = Files.newOutputStream(destPath)) {
                StreamUtils.copyStream(is, os, (buf, len) -> transferItem.setLoaded(transferItem.getLoaded() + len));
            }
            // 数据写入完成后，复制源路径文件的修改日期
            Files.setLastModifiedTime(destPath, Files.getLastModifiedTime(srcPath));
        } else {
            Files.copy(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    @Override
    public boolean move(String src, String dest, FileTransferItem item) throws IOException {
        Files.move(Paths.get(src), Paths.get(dest));
        return true;
    }

    @Override
    public boolean exist(String path) {
        return Files.exists(Paths.get(path));
    }

    @Override
    public void updateTime(String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        if (names == null || attribute == null) {
            return;
        }

        // 1. 预转换时间格式（避免在循环中重复计算）
        FileTime mTime = Optional.ofNullable(attribute.getModifyTime()).map(d -> FileTime.fromMillis(d.getTime())).orElse(null);
        FileTime aTime = Optional.ofNullable(attribute.getLastAccessTime()).map(d -> FileTime.fromMillis(d.getTime())).orElse(null);
        FileTime cTime = Optional.ofNullable(attribute.getCreateTime()).map(d -> FileTime.fromMillis(d.getTime())).orElse(null);

        // 2. 使用 Stream 处理
        names.stream()
                // 转换为 Path 对象
                .map(name -> Paths.get(path, name))
                // 过滤掉不存在的文件，增强健壮性
                .filter(Files::exists)
                .forEach(filePath -> {
                    try {
                        Files.getFileAttributeView(filePath, BasicFileAttributeView.class)
                                .setTimes(mTime, aTime, cTime);
                    } catch (IOException e) {
                        // 在 Lambda 中处理受检异常
                        log.warn("无法更新文件属性: {} -> {}", filePath, e.getMessage());
                    }
                });
    }

    @Override
    public void close() throws Exception {

    }
}
