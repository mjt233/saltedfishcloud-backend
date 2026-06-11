package com.sfc.dm.enums;

/**
 * 失效数据处理方式
 */
public enum ProcessMethod {
    /** 丢弃 */
    DISCARD,
    /** 认领 */
    CLAIM,
    /** 自动修复 */
    AUTO_REPAIR
}
