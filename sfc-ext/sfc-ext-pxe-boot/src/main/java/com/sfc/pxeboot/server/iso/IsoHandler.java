package com.sfc.pxeboot.server.iso;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

/**
 * ISO 文件处理器接口
 */
public interface IsoHandler {

    /**
     * 列出 ISO 中的文件
     *
     * @param isoResource    ISO 文件资源
     * @param pathWithinIso  ISO 内部路径
     * @return 文件名列表
     */
    List<String> listFiles(Resource isoResource, String pathWithinIso) throws IOException;

    /**
     * 获取 ISO 中的文件资源。
     * <p>文件不存在时返回 null。</p>
     * <p>返回的 Resource 在 getInputStream() 时打开 ISO，InputStream 关闭时自动释放。</p>
     *
     * @param isoResource    ISO 文件资源
     * @param pathWithinIso  ISO 内部文件路径
     * @return 文件资源，不存在时返回 null
     */
    Resource getResource(Resource isoResource, String pathWithinIso) throws IOException;

    /**
     * 在 ISO 中按文件名正则模式搜索匹配的文件条目
     *
     * @param isoResource ISO 文件资源
     * @param pattern     文件名正则表达式（如 "^vmlinuz", "^linux\\d+"）
     * @param basePath    限定搜索的 ISO 内目录路径，null 表示搜索整个 ISO
     * @return 所有匹配的文件条目列表
     */
    List<IsoFileEntry> findFilesByPattern(Resource isoResource, String pattern, String basePath) throws IOException;
}
