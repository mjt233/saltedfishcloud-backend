package com.sfc.archive.model;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

/**
 * 读取压缩包内所有文件列表的请求参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListArchiveResourcesRequest {
    /**
     * 解压缩引擎ID
     */
    @NotNull
    private String engineProviderId;

    /**
     * 要查看的压缩包资源
     */
    @NotNull
    private ResourceRequest resourceRequest;

    /**
     * 传给解压缩引擎的属性
     */
    private ArchiveEngineProperty engineProperty;
}
