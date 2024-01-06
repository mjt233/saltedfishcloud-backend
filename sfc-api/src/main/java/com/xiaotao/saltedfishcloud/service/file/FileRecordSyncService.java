package com.xiaotao.saltedfishcloud.service.file;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 文件记录服务与存储同步服务。用于解决更换了存储或文件记录数据库或文件记录服务时，记录服务中的文件数据与实际存储不一致的问题
 */
public interface FileRecordSyncService {

    /**
     * 开始执行数据同步，将存储服务的文件信息同步到文件记录服务中。
     * @param uid       待同步的用户数据的用户ID，公共为0
     * @param precise   是否使用精确同步，使用精确同步会计算文件的哈希值，这可能将会耗费大量时间。若不使用精确同步，将仅比较文件大小。
     */
    void doSync(long uid, boolean precise) throws IOException, SQLException;
}
