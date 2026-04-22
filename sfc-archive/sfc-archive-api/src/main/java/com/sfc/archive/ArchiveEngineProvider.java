package com.sfc.archive;

import com.sfc.archive.model.ArchiveProperty;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

/**
 * 压缩引擎提供者。
 */
public interface ArchiveEngineProvider {
    /**
     * 引擎唯一标识。
     *
     * @return 引擎 ID
     */
    String getId();

    /**
     * 引擎显示名称。
     *
     * @return 引擎名称
     */
    String getName();

    /**
     * 是否支持加密压缩。
     *
     * @return true 表示支持
     */
    boolean supportEncrypt();

    /**
     * 是否支持加密解压。
     *
     * @return true 表示支持
     */
    boolean supportDecrypt();

    /**
     * 获取支持压缩的扩展名列表（不带点）。
     *
     * @return 扩展名列表
     */
    Collection<String> getSupportedCompressExtensions();

    /**
     * 获取支持解压的扩展名列表（不带点）。
     *
     * @return 扩展名列表
     */
    Collection<String> getSupportedDecompressExtensions();

    /**
     * 创建压缩任务执行器。
     *
     * @param outputStream 目标输出流
     * @param property     压缩属性
     * @return 压缩任务执行器
     * @throws IOException 创建失败
     */
    ArchiveEngineCompressor createCompressor(OutputStream outputStream, ArchiveProperty property) throws IOException;

    /**
     * 创建解压任务执行器。
     *
     * @param resource  待解压资源
     * @param property  解压属性
     * @return 解压任务执行器
     * @throws IOException 创建失败
     */
    ArchiveEngineDecompressor createDecompressor(Resource resource, ArchiveProperty property) throws IOException;
}

