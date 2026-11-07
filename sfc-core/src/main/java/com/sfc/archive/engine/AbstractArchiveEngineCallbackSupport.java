package com.sfc.archive.engine;

import com.sfc.archive.model.FileTransferCallback;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * 归档引擎回调分发的通用抽象基类。
 * <p>
 * 统一封装 callback 判空、异常保护与日志记录逻辑，避免压缩与解压实现重复代码。
 * </p>
 */
@Slf4j
public abstract class AbstractArchiveEngineCallbackSupport {

    /**
     * 文件传输回调，可为null。
     */
    @Setter
    private FileTransferCallback callback;

    /**
     * 安全地调用文件开始处理回调。
     *
     * @param archivePath 压缩包内路径
     */
    protected final void invokeOnFileStart(String archivePath) {
        invokeCallback(target -> target.onFileStart(archivePath), "onFileStart");
    }

    /**
     * 安全地调用文件处理完成回调。
     *
     * @param archivePath 压缩包内路径
     */
    protected final void invokeOnFileComplete(String archivePath) {
        invokeCallback(target -> target.onFileComplete(archivePath), "onFileComplete");
    }

    /**
     * 安全地调用传输进度回调。
     *
     * @param archivePath 压缩包内路径
     * @param loaded      已处理字节数
     * @param total       总字节数
     */
    protected final void invokeOnProgress(String archivePath, long loaded, long total) {
        invokeCallback(target -> target.onProgress(archivePath, loaded, total), "onProgress");
    }

    /**
     * 内部方法：安全地执行回调操作。
     *
     * @param operation     回调操作
     * @param operationName 操作名称（用于日志）
     */
    private void invokeCallback(Consumer<FileTransferCallback> operation, String operationName) {
        if (callback == null) {
            return;
        }
        try {
            operation.accept(callback);
        } catch (Exception e) {
            log.error("执行回调 {} 时发生异常", operationName, e);
        }
    }
}

