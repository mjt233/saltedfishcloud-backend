package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.AbstractRawStoreService;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PRIVATE;
import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PUBLIC;

/**
 * 本地文件系统存储服务，网盘文件数据保存在本地文件系统当中
 */
@Slf4j
public class LocalStoreService extends AbstractRawStoreService implements ApplicationRunner {
    @Autowired
    private SysProperties sysProperties;

    private String storeRoot;
    private String publicRoot;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        storeRoot = sysProperties.getStore().getRoot();
        publicRoot = sysProperties.getStore().getPublicRoot();
    }

    public LocalStoreService(FileResourceMd5Resolver md5Resolver) {
        super(new LocalDirectRawStoreHandler(), md5Resolver);
    }

    @Override
    public boolean canBrowse() {
        return true;
    }

    @Override
    public boolean isMoveWithRecursion() {
        return false;
    }

    @Override
    public String getPublicRoot() {
        return publicRoot;
    }

    @Override
    public String getStoreRoot() {
        return storeRoot;
    }

    @Override
    public int delete(String md5) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileSystemStatus> getStatus() {
        File storeRoot = new File(sysProperties.getStore().getRoot());
        File publicRoot;
        if (sysProperties.getStore().getMode() == StoreMode.UNIQUE) {
            publicRoot = new File(getStoreRoot());
        } else {
            publicRoot = new File(sysProperties.getStore().getPublicRoot());
        }

        return Arrays.asList(
                FileSystemStatus.builder()
                        .area(AREA_PUBLIC)
                        .path(publicRoot.getAbsolutePath())
                        .free(publicRoot.getFreeSpace())
                        .total(publicRoot.getTotalSpace())
                        .used(publicRoot.getTotalSpace() - publicRoot.getFreeSpace())
                        .build(),
                FileSystemStatus.builder()
                        .area(AREA_PRIVATE)
                        .path(storeRoot.getAbsolutePath())
                        .free(storeRoot.getFreeSpace())
                        .total(storeRoot.getTotalSpace())
                        .used(storeRoot.getTotalSpace() - storeRoot.getFreeSpace())
                        .build()
        );
    }

    @Override
    public void moveToSave(long uid, Path nativePath, String diskPath, FileInfo fileInfo) throws IOException {
        final String root = getUserFileRoot(uid);
        final String savePath = StringUtils.appendPath(root, diskPath, fileInfo.getName());
        Path targetPath = Path.of(savePath);
        if (Files.exists(targetPath)) {
            if (Files.isDirectory(targetPath)) {
                log.error("本地存储目标路径 {} 已存在同名文件夹，无法保存文件", targetPath);
                throw new IllegalArgumentException("target path " + uid + ":" + diskPath + " exist directory");
            }
            handler.delete(savePath);
        }
        handler.move(nativePath.toString(), savePath);
    }
}
