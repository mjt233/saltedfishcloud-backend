package com.sfc.dm.enums;

/**
 * 失效数据类型
 */
public enum InvalidDataType {
    /** 失效文件记录(文件记录存在，但指向的物理存储不存在了） */
    FILE_RECORD,
    /** 失效物理存储(该物理存储未被文件记录指向/引用) */
    PHYSICAL_STORAGE
}
