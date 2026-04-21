package com.sfc.archive.model;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步解压任务参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AsyncArchiveExtractParam {

    /**
     * 待解压的文件来源资源请求
     */
    private ResourceRequest source;

    /**
     * 详细解压参数（格式、编码、密码等）
     */
    private ArchiveParam archiveParam;

    /**
     * 解压到的目标用户 ID
     */
    private Long uid;

    /**
     * 解压到的目标网盘目录路径
     */
    private String path;
}

