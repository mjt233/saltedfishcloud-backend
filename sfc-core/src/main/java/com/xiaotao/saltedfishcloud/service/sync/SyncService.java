package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.service.sync.detector.SyncDiffDetector;
import com.xiaotao.saltedfishcloud.service.sync.handler.SyncDiffHandler;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.entity.po.User;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;


@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class SyncService {
    private final static ReadOnlyLevel WORKING_READ_ONLY_LEVEL = ReadOnlyLevel.DATA_CHECKING;
    private final SyncDiffHandler handler;
    private final SyncDiffDetector detector;

    SyncService(SyncDiffHandler handler, SyncDiffDetector detector) {
        this.handler = handler;
        this.detector = detector;
    }

    /**
     * 同步目标用户的数据库与本地文件信息
     * @TODO 性能优化：找出被重命名的文件，目录和被移动的目录
     * @param user  用户对象信息
     * @throws IOException IO出错
     */
    public void syncLocal(User user) throws Exception {
        try {
            DiskConfig.setReadOnlyLevel(WORKING_READ_ONLY_LEVEL);
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

            DiskConfig.setReadOnlyLevel(null);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            DiskConfig.setReadOnlyLevel(null);
        }
    }
}