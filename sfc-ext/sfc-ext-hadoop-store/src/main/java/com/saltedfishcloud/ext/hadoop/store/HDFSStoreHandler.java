package com.saltedfishcloud.ext.hadoop.store;

import com.saltedfishcloud.ext.hadoop.HDFSResource;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HDFSStoreHandler implements DirectRawStoreHandler, Closeable {
    private final FileSystem fs;
    public HDFSStoreHandler(FileSystem fs) {
        this.fs = fs;
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        final List<FileInfo> list = listFiles(path);
        if (list == null) {
            return false;
        }
        return list.isEmpty();
    }

    @Override
    public Resource getResource(String path) throws IOException {
        Path target = new Path(path);
        if (!fs.exists(target) || fs.getFileStatus(target).isDirectory()) {
            return null;
        }
        return new HDFSResource(fs, target);
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        Path targetPath = new Path(path);
        if (!fs.exists(targetPath)) {
            return Collections.emptyList();
        } else {
            List<FileInfo> res = new ArrayList<>();
            for (FileStatus fileStatus : fs.listStatus(targetPath)) {
                FileInfo file;
                if (fileStatus.isDirectory()) {
                    file = new FileInfo(
                            fileStatus.getPath().getName(),
                            -1,
                            FileInfo.TYPE_DIR,
                            path,
                            fileStatus.getModificationTime(),
                            null
                    );
                } else {
                    file = new FileInfo(
                            fileStatus.getPath().getName(),
                            fileStatus.getLen(),
                            FileInfo.TYPE_FILE,
                            path,
                            fileStatus.getModificationTime(),
                            new HDFSResource(fs, fileStatus.getPath())
                    );
                }
                file.setCreateAt(new Date(System.currentTimeMillis()));
                res.add(file);
            }
            return res;
        }
    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        final Path hdfsPath = new Path(path);
        if(!fs.exists(hdfsPath)) {
            return null;
        } else {
            final FileStatus fileStatus = fs.getFileStatus(hdfsPath);
            final Path filePath = fileStatus.getPath();
            return new FileInfo(
                    filePath.getName(),
                    fileStatus.isDirectory() ? -1 : fileStatus.getLen(),
                    fileStatus.isDirectory() ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE,
                    PathUtils.getParentPath(path),
                    fileStatus.getModificationTime(),
                    null
            );
        }
    }


    @Override
    public void close() throws IOException {
        fs.close();
    }

    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        Path target = new Path(path);
        if (fs.exists(target)) {
            if (fs.getFileStatus(target).isDirectory()) {
                throw new JsonException(FileSystemError.RESOURCE_TYPE_NOT_MATCH);
            }
            fs.delete(target, false);
        }
        return fs.create(target);
    }

    @Override
    public boolean delete(String path) throws IOException {
        return fs.delete(new Path(path), true);
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        return fs.mkdirs(new Path(path));
    }

    @Override
    public long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException {
        Path target = new Path(path);
        if (fs.exists(target)) {
            if (fs.getFileStatus(target).isDirectory()) {
                throw new JsonException(FileSystemError.RESOURCE_TYPE_NOT_MATCH);
            }
            fs.delete(target, false);
        }
        long cnt = 0;
        try(final FSDataOutputStream out = fs.create(target)) {
            cnt = StreamUtils.copy(inputStream, out);
        }
        if (fileInfo != null && fileInfo.getMtime() != null) {
            fs.setTimes(target, fileInfo.getMtime(), System.currentTimeMillis());
        }
        inputStream.close();
        return cnt;
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        final Path originPath = new Path(path);
        final Path newPath = new Path(StringUtils.appendPath(PathUtils.getParentPath(path), newName));
        return fs.rename(originPath, newPath);
    }

    @Override
    public boolean copy(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        final Resource resource = getResource(src);
        try (
                final InputStream is = resource.getInputStream();
                final FSDataOutputStream os = fs.create(new Path(dest))
        ) {
            if (item != null) {
                long total = resource.contentLength();
                item.setTotal(total);
                item.setLoaded(0L);
                com.xiaotao.saltedfishcloud.utils.StreamUtils.copyStream(is, os, (buf, len) -> {
                    item.setLoaded(item.getLoaded() + len);
                });
            } else {
                StreamUtils.copy(is, os);
            }
        }
        return true;
    }

    @Override
    public boolean move(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        return fs.rename(new Path(src), new Path(dest));
    }

    @Override
    public void updateTime(String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        fs.setTimes(
                new Path(path),
                attribute.getModifyTime() == null ? -1 : attribute.getModifyTime().getTime(),
                attribute.getCreateTime() == null ? -1 : attribute.getCreateTime().getTime()
        );
    }
}
