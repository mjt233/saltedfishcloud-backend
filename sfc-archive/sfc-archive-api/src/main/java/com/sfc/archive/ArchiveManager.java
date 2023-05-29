package com.sfc.archive;

import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveParam;
import org.springframework.core.io.Resource;

import java.io.OutputStream;

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
}
