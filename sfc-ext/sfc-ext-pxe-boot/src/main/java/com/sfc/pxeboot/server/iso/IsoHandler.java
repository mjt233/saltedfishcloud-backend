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
     * @param uid           用户 ID
     * @param isoPath       ISO 文件所在目录路径
     * @param isoFileName   ISO 文件名
     * @param pathWithinIso ISO 内部路径
     * @return 文件列表
     */
    List<String> listFiles(Long uid, String isoPath, String isoFileName, String pathWithinIso) throws IOException;

    /**
     * 获取 ISO 中的文件流
     *
     * @param uid           用户 ID
     * @param isoPath       ISO 文件所在目录路径
     * @param isoFileName   ISO 文件名
     * @param pathWithinIso ISO 内部文件路径
     * @return 文件资源
     */
    Resource getFileStream(Long uid, String isoPath, String isoFileName, String pathWithinIso) throws IOException;
}
