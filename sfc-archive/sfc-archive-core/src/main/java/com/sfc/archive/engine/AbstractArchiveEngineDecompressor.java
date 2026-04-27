package com.sfc.archive.engine;

import com.sfc.archive.ArchiveEngineDecompressor;
import com.xiaotao.saltedfishcloud.exception.JsonException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 解压引擎实现的抽象基类。
 * <p>
 * 统一封装解压输入流回调分发能力，确保开始、进度与完成事件触发语义一致。
 * </p>
 */
public abstract class AbstractArchiveEngineDecompressor extends AbstractArchiveEngineCallbackSupport implements ArchiveEngineDecompressor {

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

