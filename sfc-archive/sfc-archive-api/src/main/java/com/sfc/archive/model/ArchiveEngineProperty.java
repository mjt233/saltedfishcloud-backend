package com.sfc.archive.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 传递给压缩/解压引擎的解压缩参数属性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveEngineProperty {
    /**
     * 压缩级别，默认 NORMAL。<br>
     * <strong>注意：不同的压缩器实现不一定会严格按照该参数执行，而是映射到接近的等级，或忽略该参数</strong>
     */
    @Builder.Default
    private CompressionLevel compressionLevel = CompressionLevel.NORMAL;

    /**
     * 加密参数，可为空。
     */
    private EncryptionParam encryptionParam;

    /**
     * 压缩包内文件名编码，默认 UTF-8。
     */
    @Builder.Default
    private String encoding = "UTF-8";

    /**
     * 当前引擎处理的压缩格式扩展名（带点，例如 .zip、.7z），默认 .zip。
     */
    @Builder.Default
    private String extension = ".zip";
}


