package com.xiaotao.saltedfishcloud.service.file.store.attach;

import lombok.Data;

/**
 * 附属存储域数据定义
 */
@Data
public class AttachStorageDomainDefinition {

    /**
     * 存储域唯一标识
     */
    private String id;

    /**
     * 存储域名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;
}
