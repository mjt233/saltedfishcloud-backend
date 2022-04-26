package com.saltedfishcloud.ext.hadoop.store;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HDFSStoreHandler extends HDFSReader implements DirectRawStoreHandler {
    private final FileSystem fs;
    public HDFSStoreHandler(FileSystem fs) {
        super(fs);
        this.fs = fs;
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
    public long store(String path, InputStream inputStream) throws IOException {
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
    public boolean copy(String src, String dest) throws IOException {
        final Resource resource = getResource(src);
        try(
                final InputStream is = resource.getInputStream();
                final FSDataOutputStream os = fs.create(new Path(dest))
        ) {
            StreamUtils.copy(is, os);
        }
        return true;
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        return fs.rename(new Path(src), new Path(dest));
    }
}
