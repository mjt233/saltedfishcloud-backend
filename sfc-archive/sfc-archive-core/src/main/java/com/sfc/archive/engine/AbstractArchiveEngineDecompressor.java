package com.sfc.archive.engine;

import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.function.IOExceptionBiFunction;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import lombok.extern.slf4j.Slf4j;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 解压引擎实现的抽象基类。
 * <p>
 * 统一封装解压输入流回调分发能力，确保开始、进度与完成事件触发语义一致。
 * </p>
 */
@Slf4j
public abstract class AbstractArchiveEngineDecompressor extends AbstractArchiveEngineCallbackSupport implements ArchiveEngineDecompressor {

    /**
     * 通用全量解压流程：依次遍历资源并交给回调处理。
     *
     * @param func 资源处理回调
     * @throws IOException 解压失败
     */
    @Override
    public void decompressAll(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func) throws IOException {
        log.warn("{} 未重写 decompressAll 使用默认实现", this.getClass().getName());
        requireDecompressFunction(func);

        Iterator<ArchiveResource> resources = getArchiveResources();
        while (resources.hasNext()) {
            ArchiveResource archiveResource = resources.next();
            Boolean keepGoing;
            if (Boolean.TRUE.equals(archiveResource.getIsDirectory())) {
                keepGoing = invokeDecompressFunction(func, null, archiveResource);
            } else {
                try (InputStream inputStream = getInputStream(archiveResource.getArchivePath())) {
                    keepGoing = invokeDecompressFunction(func, inputStream, archiveResource);
                }
            }

            if (!Boolean.TRUE.equals(keepGoing)) {
                return;
            }
        }
    }

    /**
     * 执行解压回调并返回是否继续处理后续资源。
     *
     * @param func          资源处理回调
     * @param inputStream   当前资源流，目录可传 {@code null}
     * @param archiveResource 当前资源信息
     * @return 返回 {@code true} 表示继续，返回 {@code false} 表示停止
     * @throws IOException 回调执行失败
     */
    protected final boolean continueDecompress(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func,
                                               InputStream inputStream,
                                               ArchiveResource archiveResource) throws IOException {
        return Boolean.TRUE.equals(invokeDecompressFunction(func, inputStream, archiveResource));
    }

    /**
     * 校验解压回调参数。
     *
     * @param func 回调参数
     */
    protected final void requireDecompressFunction(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func) {
        if (func == null) {
            throw new JsonException("func 不能为空");
        }
    }

    /**
     * 调用解压回调并统一校验输入参数。
     *
     * @param func            回调参数
     * @param inputStream     资源输入流
     * @param archiveResource 当前资源
     * @return 回调返回值
     * @throws IOException 回调执行失败
     */
    protected final Boolean invokeDecompressFunction(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func,
                                                     InputStream inputStream,
                                                     ArchiveResource archiveResource) throws IOException {
        requireDecompressFunction(func);
        if (archiveResource == null) {
            throw new JsonException("archiveResource 不能为空");
        }
        return func.apply(inputStream, archiveResource);
    }

    /**
     * 包装输入流并在读取过程中分发回调。
     *
     * @param archivePath 压缩包内路径
     * @param total       文件总字节数，未知时传 {@code -1}
     * @param inputStream 原始输入流
     * @return 带回调分发能力的输入流
     */
    protected final InputStream wrapInputStreamWithCallback(String archivePath, long total, InputStream inputStream) {
        String normalizedPath = requireArchivePath(archivePath);
        if (inputStream == null) {
            throw new JsonException("inputStream 不能为空");
        }

        invokeOnFileStart(normalizedPath);
        return new FilterInputStream(inputStream) {
            /**
             * 已读取字节数。
             */
            private long loaded;

            /**
             * 完成事件是否已触发。
             */
            private boolean completed;

            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value >= 0) {
                    loaded++;
                    invokeOnProgress(normalizedPath, loaded, total);
                } else {
                    completeIfNeeded();
                }
                return value;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int readLen = super.read(b, off, len);
                if (readLen > 0) {
                    loaded += readLen;
                    invokeOnProgress(normalizedPath, loaded, total);
                } else if (readLen < 0) {
                    completeIfNeeded();
                }
                return readLen;
            }

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    completeIfNeeded();
                }
            }

            /**
             * 确保完成回调只触发一次。
             */
            private void completeIfNeeded() {
                if (completed) {
                    return;
                }
                completed = true;
                invokeOnFileComplete(normalizedPath);
            }
        };
    }

    /**
     * 校验并去除路径前导斜杠。
     *
     * @param archivePath 原始路径
     * @return 标准化路径
     */
    protected final String normalizeArchivePath(String archivePath) {
        String value = requireArchivePath(archivePath);
        return value.startsWith("/") ? value.substring(1) : value;
    }

    /**
     * 校验路径参数非空。
     *
     * @param archivePath 原始路径
     * @return 原始路径
     */
    protected final String requireArchivePath(String archivePath) {
        if (archivePath == null || archivePath.isEmpty()) {
            throw new JsonException("archivePath 不能为空");
        }
        return archivePath;
    }
}

