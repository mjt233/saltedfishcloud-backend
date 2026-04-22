package com.sfc.archive;

import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveParam;
import org.springframework.core.io.Resource;

import java.io.OutputStream;
import java.util.Collection;

/**
 * 压缩服务管理器
 */
public interface ArchiveManager {
    /**
     * 获取一个文件压缩器
     * @param param     压缩参数，根据参数的不同可以获取到不同的压缩器
     * @param outputStream  压缩文件输出流
     * @return          压缩器
     */
    ArchiveCompressor getCompressor(ArchiveParam param, OutputStream outputStream);

    /**
     * 获取文件解压缩器
     * @param param    解压参数，根据参数的不同可以获取到不同的解压缩器
     * @param resource  待解压的文件资源
     * @return          解压缩器
     */
    ArchiveExtractor getExtractor(ArchiveParam param, Resource resource);

    /**
     * 注册压缩器
     * @param provider  压缩器提供者
     */
    void registerCompressor(ArchiveCompressorProvider provider);

    /**
     * 注册解压缩器
     * @param provider  解压缩器提供者
     */
    void registerExtractor(ArchiveExtractorProvider provider);

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
     * @return 命中的引擎，未命中返回 null
     */
    ArchiveEngineProvider getDecompressorEngineByFilename(String fileName);

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
