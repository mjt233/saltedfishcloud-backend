package com.sfc.archive;

import com.sfc.archive.model.ArchiveResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 单次解压任务执行器。
 */
public interface ArchiveEngineDecompressor extends AutoCloseable {
    /**
     * 列出压缩包中的全部资源。
     *
     * @return 资源迭代器
     * @throws IOException 读取资源列表失败
     */
    Iterator<ArchiveResource> getArchiveResources() throws IOException;

    /**
     * 获取压缩包中指定资源的输入流。
     *
     * @param archivePath 压缩包内完整路径
     * @return 资源输入流
     * @throws IOException 读取失败
     */
    InputStream getInputStream(String archivePath) throws IOException;

    /**
     * 关闭解压任务并释放资源。
     *
     * @throws IOException 关闭失败
     */
    @Override
    void close() throws IOException;
}

