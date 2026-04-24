package com.sfc.archive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 归档引擎特性详情。
 */
@JsonPropertyOrder({"engineId", "engineName", "compressExtensions", "decompressExtensions"})
public class ArchiveEngineFeatureDetail {
    /**
     * 引擎唯一标识。
     */
    private final String engineId;

    /**
     * 引擎显示名称。
     */
    private final String engineName;

    /**
     * 支持压缩的扩展名列表。
     */
    private final List<String> compressExtensions;

    /**
     * 支持解压的扩展名列表。
     */
    private final List<String> decompressExtensions;

    /**
     * 创建归档引擎特性详情。
     *
     * @param engineId             引擎唯一标识
     * @param engineName           引擎显示名称
     * @param compressExtensions   支持压缩的扩展名列表
     * @param decompressExtensions 支持解压的扩展名列表
     */
    public ArchiveEngineFeatureDetail(String engineId, String engineName, List<String> compressExtensions, List<String> decompressExtensions) {
        this.engineId = engineId;
        this.engineName = engineName;
        this.compressExtensions = compressExtensions;
        this.decompressExtensions = decompressExtensions;
    }

    /**
     * 获取引擎唯一标识。
     *
     * @return 引擎唯一标识
     */
    public String getEngineId() {
        return engineId;
    }

    /**
     * 获取引擎显示名称。
     *
     * @return 引擎显示名称
     */
    public String getEngineName() {
        return engineName;
    }

    /**
     * 获取支持压缩的扩展名列表。
     *
     * @return 支持压缩的扩展名列表
     */
    public List<String> getCompressExtensions() {
        return compressExtensions;
    }

    /**
     * 获取支持解压的扩展名列表。
     *
     * @return 支持解压的扩展名列表
     */
    public List<String> getDecompressExtensions() {
        return decompressExtensions;
    }
}
