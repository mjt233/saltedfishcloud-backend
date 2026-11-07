package com.sfc.archive;

import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.model.ArchiveParam;

import java.io.OutputStream;
import java.util.Collection;

/**
 * 压缩器提供者
 */
public interface ArchiveCompressorProvider {
    /**
     * 根据参数获取压缩器
     * @param param 压缩参数
     * @param outputStream 输出流
     * @return      压缩器
     */
    ArchiveCompressor getCompressor(ArchiveParam param, OutputStream outputStream);

    /**
     * 获取支持的压缩类型
     * @return 压缩类型集合
     */
    Collection<String> getSupportsType();
}
