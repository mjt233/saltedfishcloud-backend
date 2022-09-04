package com.saltedfishcloud.ext.hadoop.store;

import com.saltedfishcloud.ext.hadoop.HDFSResource;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.StoreReader;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HDFSReader implements StoreReader {
    private final FileSystem fs;

    public HDFSReader(FileSystem fs) {
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
                file.setCreatedAt(new Date(System.currentTimeMillis()));
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
}
