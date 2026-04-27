package com.sfc.archive.engine;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.utils.StreamUtils;
import lombok.Getter;
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

}

