package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
public class HardLinkStoreService extends RAWStoreService {

    public HardLinkStoreService(UserDao userDao) {
        super(userDao);
    }

    @Override
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        Path targetPath = Paths.get(LocalStoreConfig.rawPathHandler.getStorePath(uid, diskPath, fileInfo)); // 被移动到的目标位置
        if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
            throw new IllegalArgumentException("被覆盖的目标 " + fileInfo.getName() + " 是个目录");
        }
        // 唯一文件仓库中的路径
        Path sourcePath = Paths.get(LocalStoreConfig.uniquePathHandler.getStorePath(uid, diskPath, fileInfo)); // 文件仓库源文件路径
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
        String localSource = LocalStoreConfig.getPathHandler().getStorePath(uid, source, null);
        String localTarget = LocalStoreConfig.getPathHandler().getStorePath(targetId, target, null);
        FileUtils.copy(Paths.get(localSource),Paths.get(localTarget), sourceName, targetName, true);
    }

    @Override
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {
        // MD5仓库路径
        Path md5Target = Paths.get(LocalStoreConfig.uniquePathHandler.getStorePath(uid, targetDir, fileInfo));


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
        Path rawTarget = Paths.get(LocalStoreConfig.rawPathHandler.getStorePath(uid, targetDir, fileInfo));
        if (Files.exists(rawTarget) && Files.isDirectory(rawTarget)) {
            throw new UnableOverwriteException(409, "已存在同名目录: " + targetDir + "/" + fileInfo.getName());
        }
        FileUtils.createParentDirectory(rawTarget);
        log.debug("create hard link:" + md5Target + " <==> "  + rawTarget);
        if (Files.exists(rawTarget)) Files.delete(rawTarget);
        Files.createLink(rawTarget, md5Target);
    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        BasicFileInfo fileInfo = new BasicFileInfo(name, null);
        Path sourcePath = Paths.get(LocalStoreConfig.rawPathHandler.getStorePath(uid, source, fileInfo));
        Path targetPath = Paths.get(LocalStoreConfig.rawPathHandler.getStorePath(uid, target, fileInfo));
        FileUtils.move(sourcePath, targetPath);
    }
}
