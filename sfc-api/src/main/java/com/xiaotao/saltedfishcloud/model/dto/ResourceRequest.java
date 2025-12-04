package com.xiaotao.saltedfishcloud.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一的资源请求参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceRequest {
    /**
     * 特殊参数 - 资源创建人id
     */
    public static final String CREATE_UID = "createUId";

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
     * 文件创建日期
     */
    private Long ctime;

    /**
     * 文件修改日期
     */
    private Long mtime;

    /**
     * 是否让前端缓存
     */
    private Boolean isCache;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 文件md5
     */
    private String md5;

    /**
     * 额外参数
     */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /**
     * 是否为缩略图资源
     */
    @Builder.Default
    private Boolean isThumbnail = false;
}
