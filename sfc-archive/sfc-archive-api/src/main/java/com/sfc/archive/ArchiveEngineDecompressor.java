package com.sfc.archive;

import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.FileTransferCallback;
import com.sfc.archive.function.IOExceptionBiFunction;

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
     * 遍历并解压压缩包内的全部资源。
     * <p>
     * 回调参数中的 {@link ArchiveResource} 表示当前资源；当资源不是目录时，
     * {@link InputStream} 参数不为空且可用于读取当前资源内容；目录资源会传入 {@code null} 输入流。
     * </p>
     * <p>
     * 回调返回 {@code Boolean.TRUE} 时继续遍历；返回 {@code false} 或 {@code null} 时立即停止。
     * </p>
     *
     * @param func 资源处理回调
     * @throws IOException 解压或回调执行失败
     */
    void decompressAll(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func) throws IOException;

    /**
     * 设置文件传输回调。
     * <p>
     * 默认为空操作，实现类可覆盖以支持进度通知。
     * </p>
     *
     * @param callback 回调实现，传入 {@code null} 表示清除回调
     */
    void setCallback(FileTransferCallback callback);

    /**
     * 关闭解压任务并释放资源。
     *
     * @throws IOException 关闭失败
     */
    @Override
    void close() throws IOException;
}

