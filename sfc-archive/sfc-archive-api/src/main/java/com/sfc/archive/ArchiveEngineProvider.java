package com.sfc.archive;

import com.sfc.archive.model.ArchiveEngineProperty;
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
     * 获取支持压缩的扩展名列表（带点，全小写，例如 .zip）。
     *
     * @return 扩展名列表
     */
    Collection<String> getSupportedCompressExtensions();

    /**
     * 获取支持解压的扩展名列表（带点，全小写，例如 .zip）。
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
    ArchiveEngineCompressor createCompressor(OutputStream outputStream, ArchiveEngineProperty property) throws IOException;

    /**
     * 创建解压任务执行器。
     *
     * @param resource  待解压资源
     * @param property  解压属性
     * @return 解压任务执行器
     * @throws IOException 创建失败
     */
    ArchiveEngineDecompressor createDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException;

    /**
     * 判断读取压缩包资源列表时是否要求资源位于本地文件系统。
     * <p>
     * 当返回 {@code true} 时，若资源不在本地，调用方应拒绝执行列表读取，
     * 避免因全量落盘或随机访问导致高 IO 开销。
     * </p>
     *
     * @param resource 待解压资源
     * @param property 解压属性
     * @return {@code true} 表示必须是本地资源；{@code false} 表示可直接基于流式资源读取列表
     */
    default boolean requiresLocalResourceForList(Resource resource, ArchiveEngineProperty property) {
        // 安全默认值：未知实现按“需要本地资源”处理。
        return true;
    }
}

