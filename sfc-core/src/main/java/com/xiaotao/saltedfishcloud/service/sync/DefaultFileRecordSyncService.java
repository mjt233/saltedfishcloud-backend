package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordSyncService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.sync.detector.SyncDiffDetector;
import com.xiaotao.saltedfishcloud.service.sync.handler.SyncDiffHandler;
import com.xiaotao.saltedfishcloud.service.sync.model.FileChangeInfo;
import com.xiaotao.saltedfishcloud.service.sync.model.SyncDiffDetectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;


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
    private final StoreServiceFactory storeServiceFactory;

    @Override
    public void doSync(int uid, boolean precise) throws IOException, SQLException {
            if (!storeServiceFactory.getService().canBrowse()) {
                log.debug("[SYNC]存储服务不支持同步");
                return;
            }
            SyncDiffDetectResult result = detector.detect(uid, precise);
            List<FileInfo> deletedFiles = result.getDeletedFiles();
            List<FileChangeInfo> changeFiles = result.getChangeFiles();
            List<String> deletedDir = result.getDeletedDirPaths();
            List<String> newDir = result.getNewDirPaths();
            List<FileInfo> newFiles = result.getNewFiles();

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
