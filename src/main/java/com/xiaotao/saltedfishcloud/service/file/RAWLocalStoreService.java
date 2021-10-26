package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Collection;

@Slf4j
public class RAWLocalStoreService implements LocalStoreService {
    @Override
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        Path targetPath = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, diskPath, fileInfo)); // 被移动到的目标位置
        // 非唯一模式，直接将文件移动到目标位置
        if (!nativePath.equals(targetPath)) {
            log.debug("File move {} => {}", nativePath, targetPath);
            Files.move(nativePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {
        String localSource = DiskConfig.getPathHandler().getStorePath(uid, source, null);
        String localTarget = DiskConfig.getPathHandler().getStorePath(targetId, target, null);
        FileUtils.copy(Paths.get(localSource),Paths.get(localTarget), sourceName, targetName, false);
    }

    @Override
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {

    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {

    }

    @Override
    public void rename(int uid, String path, String oldName, String newName) throws IOException {

    }

    @Override
    public boolean mkdir(int uid, String path, String name) throws IOException {
        return false;
    }

    @Override
    public int delete(String md5) throws IOException {
        return 0;
    }

    @Override
    public long delete(int uid, String path, Collection<String> files) throws IOException {
        return 0;
    }
}
