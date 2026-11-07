package com.sfc.archive.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 压缩引擎支持的加密能力描述。
 * <p>
 * 用于声明同一引擎在不同格式下，对压缩和解压操作是否支持加密。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionCapability {
    /**
     * 文件扩展名（带点，全小写，例如 .zip、.7z、.tar.gz）。
     */
    private String extension;

    /**
     * 操作类型。
     */
    private EncryptionOperation operation;


    /**
     * 加密操作类型枚举。
     */
    public enum EncryptionOperation {
        /**
         * 压缩时加密。
         */
        COMPRESS,

        /**
         * 解压时解密。
         */
        DECOMPRESS
    }
}

