package com.xiaotao.saltedfishcloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 资源权限信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionInfo {

    /**
     * 请求的目标资源所有者用户id
     */
    private Long ownerUid;

    /**
     * 是否可写
     */
    private boolean isWritable;

    /**
     * 是否可读
     */
    private boolean isReadable;

    /**
     * 额外权限属性
     */
    private Map<String, Object> extAttrs;
}
