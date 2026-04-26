package com.sfc.archive;

import com.sfc.archive.model.ArchiveProperty;
import org.springframework.core.io.Resource;

import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

/**
 * 压缩引擎管理器，负责引擎提供者的注册、查找和压缩/解压器的创建。
 */
public interface ArchiveEngineManager {
    /**
     * 注册压缩引擎提供者。
     * @param provider 引擎提供者
     */
    void registerEngineProvider(ArchiveEngineProvider provider);

    /**
     * 根据引擎 ID 移除提供者。
     * @param providerId 引擎 ID
     */
    void removeEngineProvider(String providerId);

    /**
     * 根据引擎 ID 获取提供者。
     * @param providerId 引擎 ID
     * @return 引擎提供者
     */
    ArchiveEngineProvider getEngineProvider(String providerId);

    /**
     * 获取所有已注册引擎提供者。
     * @return 引擎列表
     */
    Collection<ArchiveEngineProvider> getEngineProviders();

    /**
     * 根据文件名匹配可解压该文件的引擎。
     * @param fileName 文件名
     * @return 命中的引擎列表，未命中返回空列表
     */
    List<ArchiveEngineProvider> getDecompressProviderByFilename(String fileName);

    /**
     * 根据引擎 ID 创建压缩器。
     * @param providerId   引擎 ID
     * @param outputStream 输出流
     * @param property     压缩属性
     * @return 压缩任务执行器
     */
    ArchiveEngineCompressor createEngineCompressor(String providerId, OutputStream outputStream, ArchiveProperty property);

    /**
     * 根据引擎 ID 创建解压器。
     * @param providerId 引擎 ID
     * @param resource   待解压资源
     * @param property   解压属性
     * @return 解压任务执行器
     */
    ArchiveEngineDecompressor createEngineDecompressor(String providerId, Resource resource, ArchiveProperty property);
}
