package com.sfc.archive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collection;
import java.util.List;

/**
 * 归档引擎特性详情。
 *
 * @param engineId               引擎唯一标识
 * @param engineName             引擎显示名称
 * @param compressExtensions     支持压缩的扩展名列表
 * @param decompressExtensions   支持解压的扩展名列表
 * @param encryptionCapabilities 支持加密的能力列表（仅包含支持加密的格式+操作组合）
 */
public record ArchiveEngineFeatureDetail(
        String engineId,
        String engineName,
        Collection<String> compressExtensions,
        Collection<String> decompressExtensions,
        Collection<EncryptionCapability> encryptionCapabilities
) {
}
