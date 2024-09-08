package com.xiaotao.saltedfishcloud.model.param;

import lombok.Builder;
import lombok.Data;

/**
 * 挂载点同步文件记录参数
 */
@Data
@Builder
public class MountPointSyncFileRecordParam {
    /**
     * 挂载点id
     */
    private Long id;

    /**
     * 文件md5缺失时，是否计算
     */
    private Boolean isComputeMd5;
}
