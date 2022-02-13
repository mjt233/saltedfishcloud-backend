package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystem;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @TODO 使存储模式切换时的迁移动作由StoreService或DiskFileSystem提供
 */
@Component
@Slf4j
public class StoreTypeSwitch {
    @Resource
    private UserDao userDao;
    @Resource
    private StoreServiceFactory storeServiceFactory;
    @Resource
    private DiskFileSystemFactory fileService;

    public void switchTo(StoreType targetType) throws IOException {
//        throw new UnsupportedOperationException("未开发完成");
        final DiskFileSystem fileSystem = fileService.getFileSystem();

        if (!(fileSystem instanceof DefaultFileSystem)) {
            throw new UnsupportedOperationException("当前文件系统或存储服务不支持切换");
        }

        doSwitch(targetType);
    }


    private void doSwitch(StoreType storeType) throws IOException {
        log.info("[STORE-SWITCH]切换到{}", storeType);
        List<User> users = userDao.getUserList();
        users.add(User.getPublicUser());

        final StoreService srcStoreService = storeType == StoreType.UNIQUE ?
                storeServiceFactory.getService().getRawStoreService() :
                storeServiceFactory.getService().getUniqueStoreService();

        final StoreService destStoreService = storeType == StoreType.UNIQUE ?
                storeServiceFactory.getService().getUniqueStoreService() :
                storeServiceFactory.getService().getRawStoreService();

        for (User user : users) {
            int uid = user.getId();
            log.debug("[STORE-SWITCH]处理用户：{}", user.getUsername());
            LinkedHashMap<String, List<FileInfo>> allFile = fileService.getFileSystem().collectFiles(user.getId(), false);

            //  文件迁移
            for (Map.Entry<String, List<FileInfo>> entry : allFile.entrySet()) {
                String dirPath = entry.getKey();
                List<FileInfo> files = entry.getValue();
                for (FileInfo file : files) {
                    if(file.isDir()) continue;
                    final String diskFullPath = StringUtils.appendPath(dirPath, file.getName());

                    // unique不支持list
                    if (storeType == StoreType.UNIQUE) {
                        if(!srcStoreService.exist(uid, diskFullPath)) {
                            log.warn("[STORE-SWITCH]未同步的文件：{}", diskFullPath);
                            continue;
                        }
                    }


                    try(final InputStream is = srcStoreService.getResource(uid, dirPath, file.getName()).getInputStream()) {
                        destStoreService.store(uid, is, dirPath, file);
                    } catch (FileNotFoundException e) {
                        log.error("[STORE-SWITCH]出错：{}/{}",dirPath, file.getName());
                        e.printStackTrace();
                    }
                }

                if(storeType == StoreType.UNIQUE) {
                    final List<String> fileNames = files.stream().map(FileInfo::getName).collect(Collectors.toList());
                    log.debug("[STORE-SWITCH]RAW => UNIQUE 目录：{} 清理旧文件：{}",dirPath, fileNames);
                    srcStoreService.delete(uid, dirPath, fileNames);
                }
            }
        }

        // 切换后，清理存储仓库
        log.debug("[STORE-SWITCH]{} => {} 切换完成，清理存储",
                storeType == StoreType.UNIQUE ? StoreType.RAW : StoreType.UNIQUE,
                storeType
        );
        srcStoreService.clear();
    }
}
