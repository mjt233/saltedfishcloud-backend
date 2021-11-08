package com.xiaotao.saltedfishcloud.service.file.localstore;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

@Slf4j
@Component
public class RAWStoreService implements StoreService {
    @Override
    public Resource getResource(int uid, String path, String name) {
        String storePath = DiskConfig.rawPathHandler.getStorePath(uid, path + "/" + name, null);
        try {
            return new UrlResource(Paths.get(storePath).toUri());
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean exist(int uid, String path) {
        String p = DiskConfig.rawPathHandler.getStorePath(uid, path, null);
        return Files.exists(Paths.get(p));
    }

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
        Path rawTarget = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, targetDir, fileInfo));
        if (Files.exists(rawTarget) && Files.isDirectory(rawTarget)) {
            throw new UnableOverwriteException(409, "已存在同名目录: " + targetDir + "/" + fileInfo.getName());
        }
        FileUtils.createParentDirectory(rawTarget);
        log.info("save file:" + rawTarget);
        Files.copy(input, rawTarget, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        PathHandler pathHandler = DiskConfig.rawPathHandler;
        BasicFileInfo fileInfo = new BasicFileInfo(name, null);
        Path sourcePath = Paths.get(pathHandler.getStorePath(uid, source, fileInfo));
        Path targetPath = Paths.get(pathHandler.getStorePath(uid, target, fileInfo));
        FileUtils.move(sourcePath, targetPath);
    }

    @Override
    public void rename(int uid, String path, String oldName, String newName) throws IOException {
        String base = DiskConfig.rawPathHandler.getStorePath(uid, path, null);
        FileUtils.rename(base, oldName, newName);
    }

    @Override
    public boolean mkdir(int uid, String path, String name) throws IOException {
        Path localFilePath = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, path, null) + "/" + name);
        Files.createDirectory(localFilePath);
        return true;
    }

    @Override
    public int delete(String md5) throws IOException {
        return FileUtils.delete(md5);
    }

    @Override
    public long delete(int uid, String path, Collection<String> files) throws IOException {
        String base = DiskConfig.rawPathHandler.getStorePath(uid, path, null);
        int cnt = 0;
        for (String file : files) {
            Path fullPath = Paths.get(base + "/" + file);
            cnt += FileUtils.delete(fullPath);
        }
        return cnt;
    }
}
