package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineDecompressor;
import com.sfc.archive.function.IOExceptionBiFunction;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.utils.EngineResourceUtils;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.springframework.core.io.Resource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 7z 解压执行器。
 */
public class SevenZArchiveEngineDecompressor extends AbstractArchiveEngineDecompressor {
    /**
     * 本地文件资源。
     */
    private final EngineResourceUtils.LocalFileResource localFileResource;

    /**
     * 解压属性。
     */
    private final ArchiveEngineProperty property;

    /**
     * 当前活跃的资源迭代器。
     */
    private final List<SevenZArchiveResourceIterator> activeResourceIterators = new ArrayList<>();

    /**
     * 创建 7z 解压器。
     *
     * @param resource 待解压资源
     * @param property 解压属性
     * @throws IOException 初始化失败
     */
    public SevenZArchiveEngineDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException {
        this.localFileResource = EngineResourceUtils.toLocalFile(resource, ".7z");
        this.property = property;
    }

    @Override
    public Iterator<ArchiveResource> getArchiveResources() throws IOException {
        SevenZArchiveResourceIterator iterator = new SevenZArchiveResourceIterator(openSevenZFile());
        synchronized (activeResourceIterators) {
            activeResourceIterators.add(iterator);
        }
        return iterator;
    }

    /**
     * 顺序遍历 7z 条目并逐个回调。
     *
     * @param func 解压回调
     * @throws IOException 解压失败
     */
    @Override
    public void decompressAll(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func) throws IOException {
        requireDecompressFunction(func);
        try (SevenZFile sevenZFile = openSevenZFile()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                ArchiveResource archiveResource = toArchiveResource(entry);
                if (entry.isDirectory()) {
                    if (!continueDecompress(func, null, archiveResource)) {
                        return;
                    }
                    continue;
                }

                try (InputStream inputStream = wrapInputStreamWithCallback(
                        archiveResource.getArchivePath(),
                        entry.getSize(),
                        sevenZFile.getInputStream(entry)
                )) {
                    if (!continueDecompress(func, inputStream, archiveResource)) {
                        return;
                    }
                }
            }
        }
    }

    @Override
    public InputStream getInputStream(String archivePath) throws IOException {
        String normalized = normalizeArchivePath(archivePath);
        SevenZFile sevenZFile = openSevenZFile();
        boolean matched = false;
        try {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory() && normalized.equals(entry.getName())) {
                    InputStream entryInputStream = sevenZFile.getInputStream(entry);

                    // 原始的 senven7File 对象是需要close释放资源的，单个文件流close后需要确保senven7File也要一起close
                    InputStream sourceInputStream = new FilterInputStream(entryInputStream) {
                        @Override
                        public void close() throws IOException {
                            try {
                                super.close();
                            } finally {
                                sevenZFile.close();
                            }
                        }
                    };
                    matched = true;
                    return wrapInputStreamWithCallback(EngineResourceUtils.normalizeArchivePath(entry.getName()), entry.getSize(), sourceInputStream);
                }
            }
        } finally {
            if (!matched) {
                sevenZFile.close();
            }
        }
        throw new JsonException("压缩包内资源不存在: " + archivePath);
    }

    @Override
    public void close() {
        try {
            synchronized (activeResourceIterators) {
                for (SevenZArchiveResourceIterator iterator : activeResourceIterators) {
                    iterator.close();
                }
                activeResourceIterators.clear();
            }
        } finally {
            localFileResource.cleanup();
        }
    }

    /**
     * 将 7z 条目转换为归档资源对象。
     *
     * <p>注意：在 Apache Commons Compress 1.28+ 中，若条目不包含某项时间戳，
     * 直接调用 {@code getLastModifiedDate()} / {@code getCreationDate()} 会抛出
     * {@link UnsupportedOperationException}。使用 try-catch 安全读取，避免因
     * 7z 文件不含该时间戳字段而导致整个列表请求失败。</p>
     *
     * @param entry 7z 条目
     * @return 归档资源
     */
    private ArchiveResource toArchiveResource(SevenZArchiveEntry entry) {
        return ArchiveResource.builder()
                .name(extractName(entry.getName()))
                .archivePath(EngineResourceUtils.normalizeArchivePath(entry.getName()))
                .isDirectory(entry.isDirectory())
                .size(entry.isDirectory() ? 0L : entry.getSize())
                .lastModified(Optional
                        .of(entry)
                        .filter(SevenZArchiveEntry::getHasLastModifiedDate)
                        .map(SevenZArchiveEntry::getLastModifiedDate)
                        .orElse(null)
                )
                .created(Optional
                        .of(entry)
                        .filter(SevenZArchiveEntry::getHasCreationDate)
                        .map(SevenZArchiveEntry::getCreationDate)
                        .orElse(null)
                )
                .build();
    }

    /**
     * 打开 SevenZFile。
     *
     * @return SevenZFile 实例
     * @throws IOException 打开失败
     */
    private SevenZFile openSevenZFile() throws IOException {
        String password = property.getEncryptionParam() == null ? null : property.getEncryptionParam().getPassword();
        SevenZFile.Builder builder = SevenZFile.builder()
                .setFile(localFileResource.getFile());

        if (password != null) {
            builder.setPassword(password);
        }
        return builder.get();
    }

    /**
     * 将 SevenZFile 的顺序读取能力封装为惰性迭代器。
     */
    private class SevenZArchiveResourceIterator implements Iterator<ArchiveResource> {
        /**
         * SevenZFile 实例。
         */
        private final SevenZFile sevenZFile;

        /**
         * 预读取的下一个条目。
         */
        private SevenZArchiveEntry nextEntry;

        /**
         * 是否已完成预读取。
         */
        private boolean prefetched;

        /**
         * 是否已关闭底层资源。
         */
        private boolean closed;

        /**
         * 创建 7z 条目迭代器。
         *
         * @param sevenZFile SevenZFile 实例
         */
        private SevenZArchiveResourceIterator(SevenZFile sevenZFile) {
            this.sevenZFile = sevenZFile;
        }

        @Override
        public boolean hasNext() {
            if (closed) {
                return false;
            }
            if (!prefetched) {
                nextEntry = readNextEntry();
                prefetched = true;
                if (nextEntry == null) {
                    close();
                    return false;
                }
            }
            return true;
        }

        @Override
        public ArchiveResource next() {
            if (!hasNext()) {
                throw new NoSuchElementException("没有更多 7z 条目");
            }
            ArchiveResource archiveResource = toArchiveResource(nextEntry);
            prefetched = false;
            nextEntry = null;
            return archiveResource;
        }

        /**
         * 读取下一个 7z 条目。
         *
         * @return 下一个条目，读取结束时返回 {@code null}
         */
        private SevenZArchiveEntry readNextEntry() {
            try {
                return sevenZFile.getNextEntry();
            } catch (IOException e) {
                close();
                throw new IllegalStateException("读取 7z 条目失败", e);
            }
        }

        /**
         * 关闭底层 SevenZFile 资源。
         */
        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                sevenZFile.close();
            } catch (IOException ignore) {
            }
            synchronized (activeResourceIterators) {
                activeResourceIterators.remove(this);
            }
        }
    }

    /**
     * 提取文件名。
     *
     * @param path 路径
     * @return 文件名
     */
    private String extractName(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

}

