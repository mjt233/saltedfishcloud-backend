package com.sfc.pxeboot.server.iso;

import org.springframework.core.io.Resource;

import java.io.Closeable;
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
     * @return 可关闭的文件资源，调用方负责在使用完毕后关闭以释放底层 ISO 文件系统
     */
    CloseableResource getFileStream(Long uid, String isoPath, String isoFileName, String pathWithinIso) throws IOException;

    /**
     * 可关闭的资源包装，持有底层 ISO 文件系统的引用。
     * 调用方必须在使用完毕后调用 {@link #close()} 以释放资源。
     */
    interface CloseableResource extends Resource, Closeable {
        /**
         * 获取文件名
         */
        String getFilename();
    }
}
