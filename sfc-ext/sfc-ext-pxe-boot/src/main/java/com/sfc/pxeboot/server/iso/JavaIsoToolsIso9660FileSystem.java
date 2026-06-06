package com.sfc.pxeboot.server.iso;

import com.github.stephenc.javaisotools.loopfs.api.FileEntry;
import com.github.stephenc.javaisotools.loopfs.api.FileSystem;
import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileEntry;
import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileSystem;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * 基于 java-iso-tools 的 ISO9660 文件系统实现。
 * <p>封装所有 java-iso-tools 依赖，实现 {@link IsoFileSystem} 接口。
 * 每次方法调用内部自行管理 ISO 文件的打开与关闭。</p>
 */
@Slf4j
public class JavaIsoToolsIso9660FileSystem implements IsoFileSystem {

    private final File isoFile;

    /**
     * 构造函数。
     *
     * @param isoFile ISO 文件
     */
    public JavaIsoToolsIso9660FileSystem(File isoFile) {
        this.isoFile = isoFile;
    }

    @Override
    public Resource getResource(String filePath) throws IOException {
        if (!exist(filePath)) {
            return null;
        }
        String normalizedPath = normalizePath(filePath);
        String targetPath = normalizedPath.substring(1);

        log.debug("[PXE-ISO] 获取 ISO 文件资源: {}, 路径: {}", isoFile, normalizedPath);

        try (FileSystem<?> fs = openIso9660()) {
            for (FileEntry entry : fs) {
                if (matchesEntry(entry, targetPath)) {
                    String fileName = Path.of(filePath).getFileName().toString();
                    long size = entry.getSize();
                    return new LazyIsoResource(isoFile, normalizedPath, fileName, size);
                }
            }
        }

        return null;
    }

    @Override
    public void traverse(Predicate<TraversalEntry> visitor) throws IOException {
        log.debug("[PXE-ISO] 遍历 ISO 所有条目: {}", isoFile);

        try (Iso9660FileSystem fs = openIso9660()) {
            for (Iso9660FileEntry entry : fs) {
                IsoFileEntry fileEntry = toFileEntry(entry);

                // 文件条目：复用已打开的 fs 获取原始 InputStream，不联动关闭 FileSystem
                // 调用方需在遍历循环内消费流，traverse 退出后流将失效
                InputStreamSupplier supplier = fileEntry.isFile() ? () -> fs.getInputStream(entry) : null;

                TraversalEntry traversalEntry = new TraversalEntry(fileEntry, supplier);
                if (!visitor.test(traversalEntry)) {
                    break;
                }
            }
        }
    }

    // ==================== Helper Methods ====================

    /**
     * 打开 ISO9660 文件系统。
     */
    private Iso9660FileSystem openIso9660() throws IOException {
        return new Iso9660FileSystem(isoFile, true);
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path.replace("\\", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private IsoFileEntry toFileEntry(Iso9660FileEntry entry) {
        IsoFileEntry.EntryType type = entry.isDirectory()
            ? IsoFileEntry.EntryType.DIRECTORY
            : IsoFileEntry.EntryType.FILE;
        boolean isNoExtension = isNoExtension(entry);
        String name;
        String path;
        if (isNoExtension) {
            name = entry.getName().substring(0, entry.getName().length() - 1);
            path = entry.getPath().substring(0, entry.getPath().length() - 1);
        } else {
            name = entry.getName();
            path = entry.getPath();
        }
        return new IsoFileEntry(name, "/" + path, entry.getSize(), entry.getLastModifiedTime(), type);
    }

    /**
     * 判断是否为没有拓展名的文件<br>
     * 没有扩展名的文件，使用java-iso-tools会读取到以"."结尾的文件名和路径
     */
    private static boolean isNoExtension(FileEntry entry) {
        return entry.getName().endsWith(".") && entry.getName().length() > 1 && entry.getName().indexOf(".") == entry.getName().length() - 1;
    }

    private static boolean matchesEntry(FileEntry entry, String target) {
        if (isNoExtension(entry)) {
            return entry.getPath().substring(0, entry.getPath().length() - 1).equalsIgnoreCase(target)
                    || entry.getName().substring(0, entry.getName().length() - 1).equalsIgnoreCase(target);
        }
        return entry.getPath().equalsIgnoreCase(target) || entry.getName().equalsIgnoreCase(target);
    }

    // ==================== Inner Classes ====================

    /**
     * 惰性 ISO 文件资源。
     */
    private static class LazyIsoResource extends AbstractResource {
        private final File isoFile;
        private final String filePath;
        private final String fileName;
        private final long size;

        LazyIsoResource(File isoFile, String filePath, String fileName, long size) {
            this.isoFile = isoFile;
            this.filePath = filePath;
            this.fileName = fileName;
            this.size = size;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @NotNull
        @Override
        public InputStream getInputStream() throws IOException {
            Iso9660FileSystem fs = new Iso9660FileSystem(isoFile, true);
            try {
                String targetPath = filePath.substring(1);
                for (Iso9660FileEntry entry : fs) {
                    if (matchesEntry(entry, targetPath)) {
                        return new LifecycleInputStream(fs.getInputStream(entry), fs);
                    }
                }
                fs.close();
                throw new IOException("ISO 中未找到文件: " + filePath);
            } catch (Exception e) {
                try {
                    fs.close();
                } catch (Exception ignored) {
                }
                throw e;
            }
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
     * 生命周期 InputStream 包装器。
     */
    private static class LifecycleInputStream extends FilterInputStream {
        private final Closeable lifecycle;
        private boolean closed = false;

        LifecycleInputStream(InputStream in, Closeable lifecycle) {
            super(in);
            this.lifecycle = lifecycle;
        }

        @Override
        @SuppressWarnings("try")
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            IOException streamEx = null;
            try {
                super.close();
            } catch (IOException e) {
                streamEx = e;
            }
            try {
                lifecycle.close();
            } catch (IOException e) {
                if (streamEx != null) {
                    streamEx.addSuppressed(e);
                    throw streamEx;
                }
                throw e;
            }
            if (streamEx != null) {
                throw streamEx;
            }
        }
    }
}
