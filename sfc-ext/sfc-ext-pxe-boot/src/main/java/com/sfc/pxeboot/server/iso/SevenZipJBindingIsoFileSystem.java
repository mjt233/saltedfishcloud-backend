package com.sfc.pxeboot.server.iso;

import lombok.extern.slf4j.Slf4j;
import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.ISeekableStream;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Date;
import java.util.function.Predicate;

/**
 * 基于 SevenZipJBinding 的 ISO 文件系统实现。
 * <p>封装所有 SevenZipJBinding 依赖，实现 {@link IsoFileSystem} 接口。
 * 每次方法调用内部自行管理 ISO 文件的打开与关闭，各个操作的 archive 实例互不关联。</p>
 */
@Slf4j
public class SevenZipJBindingIsoFileSystem implements IsoFileSystem {

    private final File isoFile;

    /**
     * 构造函数。
     *
     * @param isoFile ISO 文件
     */
    public SevenZipJBindingIsoFileSystem(File isoFile) {
        this.isoFile = isoFile;
    }

    @Override
    public void traverse(Predicate<TraversalEntry> visitor) throws IOException {
        log.debug("[PXE-ISO-7z] 遍历 ISO 所有条目: {}", isoFile);

        RandomAccessFile raf = new RandomAccessFile(isoFile, "r");
        RandomAccessFileInStream stream = new RandomAccessFileInStream(raf);
        IInArchive archive = null;
        try {
            archive = openArchive(stream);
            int itemCount = archive.getNumberOfItems();

            for (int i = 0; i < itemCount; i++) {
                final int index = i;
                IsoFileEntry fileEntry = toFileEntry(archive, index);

                // 文件条目：创建独立的 InputStreamSupplier，内部会自行打开 archive 实例
                InputStreamSupplier supplier = fileEntry.isFile() ? () -> extractByIndex(index) : null;

                TraversalEntry traversalEntry = new TraversalEntry(fileEntry, supplier);
                if (!visitor.test(traversalEntry)) {
                    break;
                }
            }
        } finally {
            closeQuietly(archive);
            closeQuietly(stream);
        }
    }

    @Override
    public Resource getResource(String filePath) throws IOException {
        if (!exist(filePath)) {
            return null;
        }

        String normalizedPath = normalizePath(filePath);
        log.debug("[PXE-ISO-7z] 获取 ISO 文件资源: {}, 路径: {}", isoFile, normalizedPath);

        // 在 traverse 中查找目标条目，获取其索引和元数据
        RandomAccessFile raf = new RandomAccessFile(isoFile, "r");
        RandomAccessFileInStream stream = new RandomAccessFileInStream(raf);
        IInArchive archive = null;
        try {
            archive = openArchive(stream);
            int itemCount = archive.getNumberOfItems();

            for (int i = 0; i < itemCount; i++) {
                try {
                    String rawPath = (String) archive.getProperty(i, PropID.PATH);
                    String itemPath = normalizePath(rawPath);

                    if (itemPath.equalsIgnoreCase(normalizedPath)) {
                        Boolean isFolder = (Boolean) archive.getProperty(i, PropID.IS_FOLDER);
                        if (Boolean.TRUE.equals(isFolder)) {
                            return null;
                        }

                        String fileName = Path.of(normalizedPath).getFileName().toString();
                        Long size = (Long) archive.getProperty(i, PropID.SIZE);
                        return new LazySevenZipResource(normalizedPath, fileName, size != null ? size : 0, i);
                    }
                } catch (SevenZipException e) {
                    throw new IOException("读取 ISO 条目属性失败", e);
                }
            }
        } finally {
            closeQuietly(archive);
            closeQuietly(stream);
        }

        return null;
    }

    // ==================== Helper Methods ====================

    /**
     * 打开 ISO 文件的 archive 实例。
     * <p>优先使用 UDF 格式打开，失败后回退到自动检测。</p>
     *
     * @param stream 已打开的输入流
     * @return archive 实例
     * @throws IOException 如果两种格式都无法打开
     */
    private IInArchive openArchive(RandomAccessFileInStream stream) throws IOException {
        try {
            return SevenZip.openInArchive(ArchiveFormat.UDF, stream);
        } catch (SevenZipException e) {
            log.debug("[PXE-ISO-7z] UDF 格式打开失败，尝试自动检测: {}", e.getMessage());
            try {
                stream.seek(0, ISeekableStream.SEEK_SET);
                return SevenZip.openInArchive(null, stream);
            } catch (SevenZipException e2) {
                throw new IOException("打开 ISO 失败: " + isoFile, e2);
            }
        }
    }

    /**
     * 独立打开 archive 实例，以流式方式提取指定索引的文件内容。
     * <p>使用管道流（{@link PipedInputStream}/{@link PipedOutputStream}）桥接 7z 推模式提取和 HTTP 响应拉模式读取，
     * 避免将整个文件缓冲到内存。资源生命周期由返回的 {@link InputStream} 关闭时触发。</p>
     *
     * @param index 文件在 archive 中的索引
     * @return 文件内容的输入流
     * @throws IOException 如果打开 archive 失败
     */
    private InputStream extractByIndex(int index) throws IOException {
        return new StreamingSevenZipResource(index).getInputStream();
    }

    /**
     * 将 archive 条目属性转换为 {@link IsoFileEntry}。
     *
     * @param archive 已打开的 archive 实例
     * @param index   条目索引
     * @return 文件条目元数据
     */
    private IsoFileEntry toFileEntry(IInArchive archive, int index) throws IOException {
        try {
            String rawPath = (String) archive.getProperty(index, PropID.PATH);
            Boolean isFolder = (Boolean) archive.getProperty(index, PropID.IS_FOLDER);
            Long size = (Long) archive.getProperty(index, PropID.SIZE);
            Date lastModified = (Date) archive.getProperty(index, PropID.LAST_MODIFICATION_TIME);

            String normalizedPath = normalizePath(rawPath);

            String name;
            if (normalizedPath.equals("/")) {
                name = "/";
            } else {
                name = Path.of(normalizedPath).getFileName().toString();
            }

            IsoFileEntry.EntryType type = Boolean.TRUE.equals(isFolder)
                    ? IsoFileEntry.EntryType.DIRECTORY
                    : IsoFileEntry.EntryType.FILE;

            long lastModifiedMillis = lastModified != null ? lastModified.getTime() : 0;
            long entrySize = Boolean.TRUE.equals(isFolder) ? -1 : (size != null ? size : 0);
            return new IsoFileEntry(name, normalizedPath, entrySize, lastModifiedMillis, type);
        } catch (SevenZipException e) {
            throw new IOException("读取 ISO 条目属性失败", e);
        }
    }

    /**
     * 规范化路径：处理反斜杠，确保以 {@code /} 开头，除根目录 {@code /} 外不以 {@code /} 结尾。
     *
     * @param path 原始路径
     * @return 规范化后的路径
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path.replace("\\", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        // 去除末尾多余的 /（根目录 "/" 除外）
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 安全关闭资源，忽略异常。
     *
     * @param closeable 可关闭资源
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== Inner Classes ====================

    /**
     * 基于 SevenZipJBinding 的惰性文件资源。
     * <p>在 {@link #getInputStream()} 调用时才打开 ISO 并提取文件内容。</p>
     */
    private class LazySevenZipResource extends AbstractResource {
        private final String filePath;
        private final String fileName;
        private final long size;
        private final int index;

        /**
         * 构造函数。
         *
         * @param filePath 文件路径（规范化后）
         * @param fileName 文件名
         * @param size     文件大小
         * @param index    文件在 archive 中的索引
         */
        LazySevenZipResource(String filePath, String fileName, long size, int index) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.size = size;
            this.index = index;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @NotNull
        @Override
        public InputStream getInputStream() throws IOException {
            return extractByIndex(index);
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public long contentLength() {
            return size;
        }

        @NotNull
        @Override
        public String getDescription() {
            return "ISO entry [" + filePath + "]";
        }
    }

    /**
     * 流式提取 ISO 文件条目的资源。
     * <p>使用 {@link PipedInputStream}/{@link PipedOutputStream} 桥接 7z 的推模式提取和 HTTP 响应的拉模式读取。
     * 提取在后台 daemon 线程中执行，读端通过 {@link #getInputStream()} 获取数据流。</p>
     * <p>最大内存占用为管道缓冲区大小（1MB），与文件大小无关，适用于大型 ISO 条目（如 2GB+ WIM 镜像）。</p>
     */
    private class StreamingSevenZipResource {
        private static final int PIPE_BUFFER_SIZE = 1024 * 1024; // 1MB

        private final int index;
        private final PipedInputStream pipedIn;
        private final PipedOutputStream pipedOut;

        StreamingSevenZipResource(int index) throws IOException {
            this.index = index;
            this.pipedOut = new PipedOutputStream();
            this.pipedIn = new PipedInputStream(pipedOut, PIPE_BUFFER_SIZE);
        }

        InputStream getInputStream() {
            Thread extractThread = new Thread(() -> {
                RandomAccessFile raf = null;
                RandomAccessFileInStream stream = null;
                IInArchive archive = null;
                try {
                    raf = new RandomAccessFile(isoFile, "r");
                    stream = new RandomAccessFileInStream(raf);
                    archive = openArchive(stream);

                    archive.extract(new int[]{index}, false, new IArchiveExtractCallback() {
                        @Override
                        public ISequentialOutStream getStream(int idx, ExtractAskMode mode) throws SevenZipException {
                            if (mode != ExtractAskMode.EXTRACT) {
                                return null;
                            }
                            return data -> {
                                try {
                                    pipedOut.write(data);
                                } catch (IOException e) {
                                    throw new SevenZipException("写入管道流失败", e);
                                }
                                return data.length;
                            };
                        }

                        @Override
                        public void prepareOperation(ExtractAskMode mode) throws SevenZipException {
                        }

                        @Override
                        public void setOperationResult(ExtractOperationResult result) throws SevenZipException {
                            if (result != ExtractOperationResult.OK) {
                                throw new SevenZipException("提取失败: " + result);
                            }
                        }

                        @Override
                        public void setCompleted(long completeValue) throws SevenZipException {
                        }

                        @Override
                        public void setTotal(long total) throws SevenZipException {
                        }
                    });
                } catch (Exception e) {
                    log.error("[PXE-ISO-7z] 流式提取 ISO 条目失败: {}, index={}", isoFile, index, e);
                } finally {
                    closeQuietly(pipedOut);
                    closeQuietly(archive);
                    closeQuietly(stream);
                }
            }, "7z-extract-" + isoFile.getName() + "[" + index + "]");
            extractThread.setDaemon(true);
            extractThread.start();

            return pipedIn;
        }
    }
}
