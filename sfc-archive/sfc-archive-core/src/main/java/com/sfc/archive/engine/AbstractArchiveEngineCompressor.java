package com.sfc.archive.engine;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.FileTransferCallback;
import com.xiaotao.saltedfishcloud.utils.StreamUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.xiaotao.saltedfishcloud.exception.JsonException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 压缩引擎实现的抽象基类。
 * <p>
 * 封装压缩过程事件的分发，提供安全的回调方法调用，防止 NPE。
 * 实现类只需关注具体的压缩逻辑，无需关心回调的 null 检查。
 * </p>
 */
@Slf4j
public abstract class AbstractArchiveEngineCompressor implements ArchiveEngineCompressor {

    /**
     * 压缩属性，包含压缩级别、加密参数和回调。
     */
    @Getter
    private final ArchiveProperty property;

    /**
     * 创建抽象压缩器。
     *
     * @param property     压缩属性
     */
    protected AbstractArchiveEngineCompressor(ArchiveProperty property) {
        this.property = property;
    }

    /**
     * 添加并压缩一个资源。
     *
     * <p>该方法统一封装参数校验、数据复制与事件回调分发。
     * 实现类仅需关注 entry 的创建与关闭。</p>
     *
     * @param resource 待压缩资源
     * @throws IOException 读取或写入失败
     */
    @Override
    public final void addArchiveResource(ArchiveResource resource) throws IOException {
        validateResource(resource);
        String normalizedPath = resource.getArchivePath();

        invokeOnFileStart(normalizedPath);
        OutputStream entryOutputStream;
        boolean entryOpened = false;
        try {
            entryOutputStream = openEntryOutputStream(resource);
            entryOpened = true;

            if (entryOutputStream != null) {
                try (InputStream in = resource.getResource().getInputStream()) {
                    AtomicLong loaded = new AtomicLong();
                    long total = StreamUtils.copyStream(in, entryOutputStream, (buf, len) -> invokeOnProgress(normalizedPath, loaded.addAndGet(len), resource.getSize()));
                    invokeOnProgress(normalizedPath, loaded.get(), total);
                }
            }
        } finally {
            if (entryOpened) {
                closeCurrentEntry();
            }
        }
        invokeOnFileComplete(normalizedPath);
    }

    /**
     * 打开当前资源对应的 entry 输出流。
     *
     * <p>实现类通常在此方法中执行类似 putNextEntry/putArchiveEntry 的操作，
     * 并返回用于写入文件内容的输出流；若返回 null，表示无需由模板方法执行数据复制。</p>
     *
     * @param resource       资源信息
     * @return entry 输出流；返回 null 表示跳过模板数据复制
     * @throws IOException 打开 entry 失败
     */
    protected abstract OutputStream openEntryOutputStream(ArchiveResource resource) throws IOException;

    /**
     * 关闭当前 entry。
     *
     * <p>实现类应在 {@link #doCloseCurrentEntry()} 中执行具体引擎的 closeEntry 行为。</p>
     *
     * @throws IOException 关闭失败
     */
    protected final void closeCurrentEntry() throws IOException {
        doCloseCurrentEntry();
    }

    /**
     * 执行引擎相关的 entry 关闭逻辑。
     *
     * @throws IOException 关闭失败
     */
    protected abstract void doCloseCurrentEntry() throws IOException;

    /**
     * 校验资源参数。
     *
     * @param resource 待校验资源
     */
    protected void validateResource(ArchiveResource resource) {
        if (resource == null) {
            throw new JsonException("archive resource 不能为空");
        }
        boolean directory = Boolean.TRUE.equals(resource.getIsDirectory());
        if (!directory && resource.getResource() == null) {
            throw new JsonException("文件资源 resource 不能为空");
        }
        String archivePath = resource.getArchivePath();
        if (archivePath == null || archivePath.isEmpty()) {
            throw new JsonException("archivePath 不能为空");
        }
    }

    /**
     * 安全地调用文件开始处理回调。
     * <p>
     * 如果回调不存在或执行过程中出现异常，将被捕获并记录，不会影响压缩流程。
     * </p>
     *
     * @param archivePath 压缩包内路径
     */
    protected void invokeOnFileStart(String archivePath) {
        invokeCallback(callback -> callback.onFileStart(archivePath), "onFileStart");
    }

    /**
     * 安全地调用文件处理完成回调。
     * <p>
     * 如果回调不存在或执行过程中出现异常，将被捕获并记录，不会影响压缩流程。
     * </p>
     *
     * @param archivePath 压缩包内路径
     */
    protected void invokeOnFileComplete(String archivePath) {
        invokeCallback(callback -> callback.onFileComplete(archivePath), "onFileComplete");
    }

    /**
     * 安全地调用传输进度回调。
     * <p>
     * 如果回调不存在或执行过程中出现异常，将被捕获并记录，不会影响压缩流程。
     * </p>
     *
     * @param archivePath 压缩包内路径
     * @param loaded      已处理字节数
     * @param total       总字节数
     */
    protected void invokeOnProgress(String archivePath, long loaded, long total) {
        invokeCallback(callback -> callback.onProgress(archivePath, loaded, total), "onProgress");
    }

    /**
     * 内部方法：安全地执行回调操作。
     * <p>
     * 检查回调是否存在，存在则执行，异常时捕获并记录。
     * </p>
     *
     * @param operation     回调操作
     * @param operationName 操作名称（用于日志）
     */
    private void invokeCallback(CallbackOperation operation, String operationName) {
        if (property == null) {
            return;
        }
        FileTransferCallback callback = property.getCallback();
        if (callback == null) {
            return;
        }
        try {
            operation.execute(callback);
        } catch (Exception e) {
            log.error("执行回调 {} 时发生异常", operationName, e);
        }
    }

    /**
     * 回调操作函数接口。
     */
    @FunctionalInterface
    private interface CallbackOperation {
        /**
         * 执行回调操作。
         *
         * @param callback 文件传输回调
         * @throws Exception 操作异常
         */
        void execute(FileTransferCallback callback) throws Exception;
    }
}

