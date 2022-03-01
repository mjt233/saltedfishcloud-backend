package com.xiaotao.saltedfishcloud.orm.config.enums;

/**
 * 配置实体类类型
 */
public enum EntityType {
    /**
     * 每个属性单独一个配置节点属性
     */
    PROPERTIES,
    /**
     * 使用整个实体对象作为一个配置节点属性
     */
    OBJECT
}
