package com.sfc.archive.model;

/**
 * 压缩级别定义。
 */
public enum CompressionLevel {
    /** 仅存储，不压缩。 */
    STORE,
    /** 最快压缩，压缩率较低。 */
    FASTEST,
    /** 快速压缩，压缩率较低。 */
    FAST,
    /** 常规压缩，压缩率与速度均衡。 */
    NORMAL,
    /** 高压缩，压缩率较高。 */
    HIGH,
    /** 极限压缩，压缩率最高。 */
    ULTRA
}

