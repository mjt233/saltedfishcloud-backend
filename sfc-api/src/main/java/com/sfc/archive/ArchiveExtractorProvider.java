package com.sfc.archive;

import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveParam;
import org.springframework.core.io.Resource;

import java.util.Collection;

/**
 * 解压器提供者
 */
public interface ArchiveExtractorProvider {

    /**
     * 根据参数获取解压缩器
     * @param param 压缩参数
     * @param resource 待解压文件的资源
     * @return      压缩器
     */
    ArchiveExtractor getExtractor(ArchiveParam param, Resource resource);

    /**
     * 获取支持的压缩类型
     * @return 压缩类型集合
     */
    Collection<String> getSupportsType();
}
