package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.ConfigDao;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Component
@Slf4j
public class StoreTypeSwitch {
    @Resource
    private UserDao userDao;
    @Resource
    private StoreService storeService;
    @Resource
    private ConfigDao configDao;
    @Resource
    private FileService fileService;

    public void switchTo(StoreType targetType) throws IOException {
        if (targetType == StoreType.RAW) switchToRaw();
        else switchToUnique();
    }

    private void switchToRaw() throws IOException {
        log.info("切换到RAW");
        if (!Files.exists(Paths.get(DiskConfig.getRawStoreRoot()))) Files.createDirectories(Paths.get(DiskConfig.getRawStoreRoot()));
        List<User> users = userDao.getUserList();

        // 1.0.0 -> 1.1.0切换兼容
        String v = configDao.getConfigure(ConfigName.VERSION);
        if (v != null) {
            users.add(User.getPublicUser());
        }
        for (User user : users) {
            int uid = user.getId();
            log.info("Processing user data: " + user.getUsername());
            Map<String, List<FileInfo>> allFile = fileService.collectFiles(uid, false);
            for (Map.Entry<String, List<FileInfo>> entry : allFile.entrySet()) {
                String p = entry.getKey();
                List<FileInfo> files = entry.getValue();

                Path dirPath = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, p, null));
                if (Files.exists(dirPath)) FileUtils.delete(dirPath);
                Files.createDirectory(dirPath);
                log.info("Create Dir " + dirPath);
                for (FileInfo file : files) {
                    if (file.isDir()) continue;
                    Path source = Paths.get(DiskConfig.uniquePathHandler.getStorePath(uid, p, file));
                    Path target = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, p, file));
                    log.info("Copy file: " + source + " -> " + target);
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        //  清理存储库
        FileUtils.delete(Paths.get(DiskConfig.getUniqueStoreRoot()));
    }


    private void switchToUnique() throws IOException {
        log.info("切换到Unique");
        if (!Files.exists(Paths.get(DiskConfig.getUniqueStoreRoot()))) Files.createDirectories(Paths.get(DiskConfig.getUniqueStoreRoot()));
        List<User> users = userDao.getUserList();
        users.add(User.getPublicUser());
        for (User user : users) {
            log.info("Processing user data: " + user.getUsername());
            LinkedHashMap<String, List<FileInfo>> allFile = fileService.collectFiles(user.getId(), false);
            //  创建本地文件
            for (Map.Entry<String, List<FileInfo>> entry : allFile.entrySet()) {
                String path = entry.getKey();
                List<FileInfo> v = entry.getValue();
                for (FileInfo fileInfo : v) {
                    if(fileInfo.isDir()) continue;
                    Path source = Paths.get(DiskConfig.rawPathHandler.getStorePath(user.getId(), path, fileInfo));
                    String target = DiskConfig.uniquePathHandler.getStorePath(user.getId(), path, fileInfo);
                    log.info("Copy file: " + source + " -> " + target);
                    storeService.store(user.getId(), Files.newInputStream(source), path, fileInfo);
                }
            }
        }
    }
}
