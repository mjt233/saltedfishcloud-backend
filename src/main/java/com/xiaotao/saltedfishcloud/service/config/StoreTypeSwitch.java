package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.localstore.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StoreTypeSwitch {
    @Resource
    private UserDao userDao;
    @Resource
    private StoreServiceFactory storeServiceFactory;
    @Resource
    private ConfigDao configDao;
    @Resource
    private DiskFileSystemFactory fileService;

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
            Map<String, List<FileInfo>> allFile = fileService.getFileSystem().collectFiles(uid, false);
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
                    if (!Files.exists(source)) {
                        log.warn("存储库文件丢失：" + file.getName() + " MD5:" + file.getMd5());
                        continue;
                    }
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
            LinkedHashMap<String, List<FileInfo>> allFile = fileService.getFileSystem().collectFiles(user.getId(), false);
            //  创建本地文件
            for (Map.Entry<String, List<FileInfo>> entry : allFile.entrySet()) {
                String path = entry.getKey();
                List<FileInfo> v = entry.getValue();
                for (FileInfo fileInfo : v) {
                    if(fileInfo.isDir()) continue;
                    Path source = Paths.get(DiskConfig.rawPathHandler.getStorePath(user.getId(), path, fileInfo));
                    String target = DiskConfig.uniquePathHandler.getStorePath(user.getId(), path, fileInfo);
                    if (!Files.exists(source)) {
                        log.warn("未同步的文件：" + path + "/" + fileInfo.getName());
                        continue;
                    }
                    log.info("Copy file: " + source + " -> " + target);
                    storeServiceFactory.getService().store(user.getId(), Files.newInputStream(source), path, fileInfo);
                }
            }
        }
    }
}
