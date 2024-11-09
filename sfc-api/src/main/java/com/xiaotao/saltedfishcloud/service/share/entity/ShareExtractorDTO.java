package com.xiaotao.saltedfishcloud.service.share.entity;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
public class ShareExtractorDTO {
    @NotNull
    private Long sid;
    @NotBlank
    private String verification;
    private String code;

    /**
     * 文件在被分享目录中所处的目录，当被提取的分享类型为目录时需提供此参数，文件类型的分享该参数可为null
     */
    private String path;
    /**
     * 要获取的资源的文件名，当被提取的分享类型为目录时需提供此参数，文件类型的分享该参数可为null
     */
    private String name;

    /**
     * 是否为获取缩略图
     */
    private boolean isThumbnail;
}
