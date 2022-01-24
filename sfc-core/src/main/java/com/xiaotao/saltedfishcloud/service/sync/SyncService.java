package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DiskFileSystemFactoryImpl;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.impl.store.RAWStoreService;
import com.xiaotao.saltedfishcloud.service.file.impl.store.StoreServiceFactoryImpl;
import com.xiaotao.saltedfishcloud.service.sync.detector.SyncDiffDetector;
import com.xiaotao.saltedfishcloud.service.sync.handler.SyncDiffHandler;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.entity.po.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;


@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class SyncService {
    private final static ReadOnlyLevel WORKING_READ_ONLY_LEVEL = ReadOnlyLevel.DATA_CHECKING;
    private final SyncDiffHandler handler;
    private final SyncDiffDetector detector;
    private final StoreServiceFactory storeServiceFactory;
    private final DiskFileSystemFactory diskFileSystemFactory;

    /**
     * 同步目标用户的数据库与本地文件信息
     * @param user  用户对象信息
     * @throws IOException IO出错
     */
    public void syncLocal(User user) throws Exception {
        if (!(diskFileSystemFactory.getFileSystem() instanceof DiskFileSystemFactoryImpl) ||
                !(storeServiceFactory instanceof StoreServiceFactoryImpl) ||
                !(storeServiceFactory.getService() instanceof RAWStoreService)
        ) {
            log.warn("当前文件系统服务不支持同步功能");
            return;
        }
        try {
            LocalStoreConfig.setReadOnlyLevel(WORKING_READ_ONLY_LEVEL);
            var result = detector.detect(user);
            var deletedFiles = result.getDeletedFiles();
            var changeFiles = result.getChangeFiles();
            var deletedDir = result.getDeletedDirPaths();
            var newDir = result.getNewDirPaths();
            var newFiles = result.getNewFiles();

            handler.handleDirDel(user, deletedDir);
            handler.handleFileDel(user, deletedFiles);
            handler.handleDirAdd(user, newDir);
            handler.handleFileAdd(user, newFiles);
            handler.handleFileChange(user, changeFiles);

            log.debug("==== 任务统计 ====");
            log.debug("被删除的目录数：{}", deletedDir.size());
            log.debug("新增的目录数：{}", newDir.size());
            log.debug("新增的文件数：{}", newFiles.size());
            log.debug("被更改的文件数：{}" , changeFiles.size());
            log.debug("被删除的文件数：{}" , deletedFiles.size());
            log.debug("==== 任务完成 ====");

            LocalStoreConfig.setReadOnlyLevel(null);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            LocalStoreConfig.setReadOnlyLevel(null);
        }
    }
}
