package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.service.file.FileRecordSyncService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceProvider;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import com.xiaotao.saltedfishcloud.service.sync.detector.SyncDiffDetector;
import com.xiaotao.saltedfishcloud.service.sync.handler.SyncDiffHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.SQLException;


/**
 * 默认的文件记录同步服务
 * @TODO 编写文件记录同步服务的自动配置类
 */
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class DefaultFileRecordSyncService implements FileRecordSyncService {
    private final SyncDiffHandler handler;
    private final SyncDiffDetector detector;
    private final StoreServiceProvider storeServiceProvider;

    @Override
    public void doSync(int uid, boolean precise) throws IOException, SQLException {
            if (!storeServiceProvider.getService().canBrowse()) {
                log.debug("[SYNC]存储服务不支持同步");
                return;
            }
            var result = detector.detect(uid, precise);
            var deletedFiles = result.getDeletedFiles();
            var changeFiles = result.getChangeFiles();
            var deletedDir = result.getDeletedDirPaths();
            var newDir = result.getNewDirPaths();
            var newFiles = result.getNewFiles();

            handler.handleDirDel(uid, deletedDir);
            handler.handleFileDel(uid, deletedFiles);
            handler.handleDirAdd(uid, newDir);
            handler.handleFileAdd(uid, newFiles);
            handler.handleFileChange(uid, changeFiles);

            log.debug("==== 任务统计 ====");
            log.debug("被删除的目录数：{}", deletedDir.size());
            log.debug("新增的目录数：{}", newDir.size());
            log.debug("新增的文件数：{}", newFiles.size());
            log.debug("被更改的文件数：{}" , changeFiles.size());
            log.debug("被删除的文件数：{}" , deletedFiles.size());
            log.debug("==== 任务完成 ====");
    }

}
