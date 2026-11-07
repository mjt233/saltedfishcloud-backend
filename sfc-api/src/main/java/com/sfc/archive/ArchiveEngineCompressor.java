package com.sfc.archive;

import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.FileTransferCallback;

import java.io.IOException;

/**
 * 单次压缩任务执行器。
 */
public interface ArchiveEngineCompressor extends AutoCloseable {
    /**
     * 添加并立即压缩一个资源。
     *
     * @param resource 待压缩资源
     * @throws IOException 资源读取或写入失败
     */
    void addArchiveResource(ArchiveResource resource) throws IOException;

    /**
     * 设置文件传输回调。
     *
     * @param callback 回调实现，传入 {@code null} 表示清除回调
     */
    void setCallback(FileTransferCallback callback);

    /**
     * 关闭压缩任务并释放资源。
     *
     * @throws IOException 关闭失败
     */
    @Override
    void close() throws IOException;
}

