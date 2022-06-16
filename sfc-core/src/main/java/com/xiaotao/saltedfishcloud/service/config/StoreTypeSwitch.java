package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceProvider;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystem;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StoreTypeSwitch {
    @Autowired
    private UserDao userDao;
    @Autowired
    private StoreServiceProvider storeServiceProvider;

    @Autowired
    private DiskFileSystemProvider fileService;

    public void switchTo(StoreMode targetType) throws IOException {
//        throw new UnsupportedOperationException("未开发完成");
        final DiskFileSystem fileSystem = fileService.getFileSystem();

        if (!(fileSystem instanceof DefaultFileSystem)) {
            throw new UnsupportedOperationException("当前文件系统或存储服务不支持切换");
        }

        doSwitch(targetType);
    }


    private void doSwitch(StoreMode targetMode) throws IOException {
        log.info("[Store Switch]切换到{}", targetMode);
        List<User> users = userDao.getUserList();
        users.add(User.getPublicUser());

        final StoreService srcStoreService = targetMode == StoreMode.UNIQUE ?
                storeServiceProvider.getService().getRawStoreService() :
                storeServiceProvider.getService().getUniqueStoreService();

        final StoreService destStoreService = targetMode == StoreMode.UNIQUE ?
                storeServiceProvider.getService().getUniqueStoreService() :
                storeServiceProvider.getService().getRawStoreService();

        for (User user : users) {
            int uid = user.getId();
            log.debug("[Store Switch]处理用户：{}", user.getUsername());
            LinkedHashMap<String, List<FileInfo>> allFile = fileService.getFileSystem().collectFiles(user.getId(), false);

            //  文件迁移
            for (Map.Entry<String, List<FileInfo>> entry : allFile.entrySet()) {
                String dirPath = entry.getKey();
                List<FileInfo> files = entry.getValue();
                for (FileInfo file : files) {
                    if(file.isDir()) {
                        if (targetMode == StoreMode.RAW) {
                            destStoreService.mkdir(uid, dirPath, file.getName());
                        }
                        continue;
                    };
                    final String diskFullPath = StringUtils.appendPath(dirPath, file.getName());

                    // unique不支持list
                    if (targetMode == StoreMode.UNIQUE) {
                        if(!srcStoreService.exist(uid, diskFullPath)) {
                            log.warn("[Store Switch]未同步的文件：{}", diskFullPath);
                            continue;
                        }
                    }

                    final Resource resource = srcStoreService.getResource(uid, dirPath, file.getName());
                    if (resource == null) {
                        log.warn("[Store Switch]文件不存在或未同步，uid: {}, dir: {}, fileName: {}", uid, dirPath, file);
                        continue;
                    }
                    try(final InputStream is = resource.getInputStream()) {
                        destStoreService.store(uid, is, dirPath, file);
                    } catch (FileNotFoundException e) {
                        log.error("[Store Switch]出错：{}/{}",dirPath, file.getName());
                        e.printStackTrace();
                    }
                }
            }
        }

        // 切换后，清理存储仓库
        log.debug("[Store Switch]{} => {} 切换完成，清理存储",
                targetMode == StoreMode.UNIQUE ? StoreMode.RAW : StoreMode.UNIQUE,
                targetMode
        );
        srcStoreService.clear();
    }
}
