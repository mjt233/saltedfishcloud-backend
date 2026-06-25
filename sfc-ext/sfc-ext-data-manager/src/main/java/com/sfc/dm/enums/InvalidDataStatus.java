package com.sfc.dm.enums;

/**
 * 失效数据状态
 */
public enum InvalidDataStatus {
    /** 待处理 */
    PENDING,
    /** 已发布可认领（仅UNIQUE模式） */
    PUBLISHED,
    /** 已认领（仅UNIQUE模式） */
    CLAIMED,
    /** 处理完成 */
    COMPLETED
}
