package com.xiaotao.saltedfishcloud.service.file.localstore;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

@Slf4j
@Component
public class HardLinkStoreService implements StoreService {
    @Override
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        Path targetPath = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, diskPath, fileInfo)); // 被移动到的目标位置
        // 唯一文件仓库中的路径
        Path sourcePath = Paths.get(DiskConfig.uniquePathHandler.getStorePath(uid, diskPath, fileInfo)); // 文件仓库源文件路径
        if (Files.exists(sourcePath)) {
            // 已存在相同文件时，直接删除本地文件
            log.debug("file md5 HIT: {}", fileInfo.getMd5());
            Files.delete(nativePath);
            if (Files.exists(targetPath)) {
                Files.delete(targetPath);
            }
        } else {
            // 将本地文件移动到唯一仓库
            log.debug("file md5 NOT HIT: {}", fileInfo.getMd5());
            FileUtils.createParentDirectory(sourcePath);
            Files.move(nativePath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
        }
        // 在目标网盘位置创建文件仓库中的文件链接
        log.debug("Create file link: {} <==> {}", targetPath, sourcePath);
        Files.createLink(targetPath, sourcePath);
    }

    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {
        String localSource = DiskConfig.getPathHandler().getStorePath(uid, source, null);
        String localTarget = DiskConfig.getPathHandler().getStorePath(targetId, target, null);
        FileUtils.copy(Paths.get(localSource),Paths.get(localTarget), sourceName, targetName, true);
    }

    @Override
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {
        // MD5仓库路径
        Path md5Target = Paths.get(DiskConfig.uniquePathHandler.getStorePath(uid, targetDir, fileInfo));


        // 先操作总仓库
        if (Files.exists(md5Target)) {
            // 重复文件命中
            log.debug("file md5 HIT:" + fileInfo.getMd5());
            if (Files.size(md5Target) != fileInfo.getSize()) {
                throw new DuplicateKeyException("文件MD5冲突");
            }
        } else {
            // 新文件
            log.debug("file md5 NOT HIT, saving:" + fileInfo.getMd5());
            FileUtils.createParentDirectory(md5Target);
            Files.copy(input, md5Target, StandardCopyOption.REPLACE_EXISTING);
        }

        // 操作用户目录，建立仓库文件与用户文件的链接
        Path rawTarget = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, targetDir, fileInfo));
        if (Files.exists(rawTarget) && Files.isDirectory(rawTarget)) {
            throw new UnableOverwriteException(409, "已存在同名目录: " + targetDir + "/" + fileInfo.getName());
        }
        FileUtils.createParentDirectory(rawTarget);
        log.info("create hard link:" + md5Target + " <==> "  + rawTarget);
        if (Files.exists(rawTarget)) Files.delete(rawTarget);
        Files.createLink(rawTarget, md5Target);
    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        BasicFileInfo fileInfo = new BasicFileInfo(name, null);
        Path sourcePath = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, source, fileInfo));
        Path targetPath = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, target, fileInfo));
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
        if (Files.exists(localFilePath)) {
            return Files.isDirectory(localFilePath);
        }
        Files.createDirectories(localFilePath);
        return true;
    }

    @Override
    public int delete(String md5) throws IOException {
        return FileUtils.delete(md5);
    }

    @Override
    public long delete(int uid, String path, Collection<String> files) throws IOException {
        String basePath = DiskConfig.rawPathHandler.getStorePath(uid, path, null);
        int res = 0;
        for (String file : files) {
            res += FileUtils.delete(Paths.get(basePath + "/" + file));
        }
        return res;
    }
}
