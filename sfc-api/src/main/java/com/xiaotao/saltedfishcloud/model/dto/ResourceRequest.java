package com.xiaotao.saltedfishcloud.model.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 统一的资源请求参数
 */
@Data
public class ResourceRequest {
    /**
     * 请求的资源所在路径
     */
    @NotNull
    private String path;

    /**
     * 资源文件名
     */
    @NotNull
    private String name;

    /**
     * 请求的资源协议
     */
    @NotNull
    private String protocol;

    /**
     * 目标资源的id
     */
    @NotNull
    private String targetId;

    /**
     * 额外参数
     */
    private Map<String, Object> params;

    /**
     * 是否为缩略图资源
     */
    private Boolean isThumbnail = false;
}
