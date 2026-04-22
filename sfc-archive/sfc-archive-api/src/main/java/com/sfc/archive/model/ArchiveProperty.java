package com.sfc.archive.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 压缩/解压属性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveProperty {
    /**
     * 压缩级别，默认 NORMAL。
     */
    @Builder.Default
    private CompressionLevel compressionLevel = CompressionLevel.NORMAL;

    /**
     * 加密参数，可为空。
     */
    private EncryptionParam encryptionParam;

    /**
     * 文件传输回调，可为空。
     */
    private FileTransferCallback callback;

    /**
     * 压缩包内文件名编码，默认 UTF-8。
     */
    @Builder.Default
    private String encoding = "UTF-8";
}


