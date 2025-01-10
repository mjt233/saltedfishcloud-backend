package com.xiaotao.saltedfishcloud.utils.db;

/**
 * query wrapper的空值处理策略
 */
public enum NullStrategy {
    /**
     * 直接使用null值作为条件。如: uid = null
     */
    USE_DIRECT,
    /**
     * 忽略该条件
     */
    IGNORE,
    /**
     * 转为IS NULL条件
     */
    TO_IS_NULL
}
