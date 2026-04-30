package com.sfc.archive.engine;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import lombok.Getter;
import com.xiaotao.saltedfishcloud.exception.JsonException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/**
 * 压缩引擎实现的抽象基类。
 * <p>
 * 封装压缩过程事件的分发，提供安全的回调方法调用，防止 NPE。
 * 实现类只需关注具体的压缩逻辑，无需关心回调的 null 检查。
 * </p>
 */
public abstract class AbstractArchiveEngineCompressor extends AbstractArchiveEngineCallbackSupport implements ArchiveEngineCompressor {

    /**
     * 压缩属性，包含压缩级别与加密参数。
     */
    @Getter
    private final ArchiveEngineProperty property;

    /**
     * 创建抽象压缩器。
     *
     * @param property     压缩属性
     */
    protected AbstractArchiveEngineCompressor(ArchiveEngineProperty property) {
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
                    long total = copyResourceDataWithProgress(in, entryOutputStream, normalizedPath, resource.getSize());
                    invokeOnProgress(normalizedPath, total, total);
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
     * 复制资源内容并分发进度事件。
     *
     * @param inputStream   资源输入流
     * @param outputStream  entry 输出流
     * @param archivePath   归档路径
     * @param expectedTotal 资源预估总大小，可为 {@code null}
     * @return 实际复制字节数
     * @throws IOException 读写失败
     */
    private long copyResourceDataWithProgress(InputStream inputStream,
                                              OutputStream outputStream,
                                              String archivePath,
                                              Long expectedTotal) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long loaded = 0;
        int len;
        while (true) {
            checkCompressionInterrupted();
            len = inputStream.read(buffer, 0, buffer.length);
            if (len < 0) {
                break;
            }
            outputStream.write(buffer, 0, len);
            loaded += len;
            invokeOnProgress(archivePath, loaded, expectedTotal);
        }
        return loaded;
    }

    /**
     * 检查当前压缩线程是否已被中断。
     *
     * @throws InterruptedIOException 线程已中断
     */
    private void checkCompressionInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("压缩任务已中断");
        }
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

}

