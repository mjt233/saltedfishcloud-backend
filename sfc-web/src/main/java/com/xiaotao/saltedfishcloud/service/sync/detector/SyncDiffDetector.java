package com.xiaotao.saltedfishcloud.service.sync.detector;

import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.service.sync.model.SyncDiffDetectResult;

/**
 * 检测数据库与本地硬盘的网盘文件差异以实现数据同步
 */
public interface SyncDiffDetector {

    /**
     * 检测指定用户数据库中记录的文件信息与本地硬盘实际存储的文件信息差异
     * @param user  被检测的用户，可以是公共用户
     * @return      检测结果集合信息类
     */
    SyncDiffDetectResult detect(User user) throws Exception;
}
