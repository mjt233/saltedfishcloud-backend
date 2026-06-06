package com.sfc.pxeboot.server.iso;

import com.github.stephenc.javaisotools.loopfs.api.FileEntry;
import com.github.stephenc.javaisotools.loopfs.api.FileSystem;
import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileSystem;
import com.github.stephenc.javaisotools.loopfs.udf.UDFFileSystem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Java ISO 处理器
 * 使用 java-iso-tools 支持 ISO9660 和 UDF 格式
 */
@Slf4j
public class JavaIsoHandler implements IsoHandler {

    @Override
    public List<String> listFiles(Resource isoResource, String pathWithinIso) throws IOException {
        Path isoLocalPath = getLocalIsoPath(isoResource);
        String normalizedPath = normalizePath(pathWithinIso);

        log.debug("[PXE-ISO] 列出 ISO 文件: {}, 路径: {}", isoLocalPath, normalizedPath);

        // 尝试以 ISO9660 格式打开
        try (FileSystem<?> fs = openIso9660(isoLocalPath)) {
            return listFilesFromFs(fs, normalizedPath);
        } catch (Exception e) {
            log.debug("[PXE-ISO] ISO9660 格式打开失败，尝试 UDF 格式: {}", e.getMessage());
        }

        // 尝试以 UDF 格式打开
        try (FileSystem<?> fs = openUdf(isoLocalPath)) {
            return listFilesFromFs(fs, normalizedPath);
        } catch (Exception e) {
            throw new IOException("无法识别 ISO 文件格式: " + isoLocalPath, e);
        }
    }

    @Override
    public CloseableResource getFileStream(Resource isoResource, String pathWithinIso) throws IOException {
        Path isoLocalPath = getLocalIsoPath(isoResource);
        String normalizedPath = normalizePath(pathWithinIso);

        log.debug("[PXE-ISO] 获取 ISO 文件流: {}, 路径: {}", isoLocalPath, normalizedPath);

        // 尝试以 ISO9660 格式打开
        try {
            return getFileFromIso9660(isoLocalPath, normalizedPath, pathWithinIso);
        } catch (Exception e) {
            log.debug("[PXE-ISO] ISO9660 格式打开失败，尝试 UDF 格式: {}", e.getMessage());
        }

        // 尝试以 UDF 格式打开
        try {
            return getFileFromUdf(isoLocalPath, normalizedPath, pathWithinIso);
        } catch (Exception e) {
            throw new IOException("无法识别 ISO 文件格式: " + isoLocalPath, e);
        }
    }

    /**
     * 从 Resource 获取 ISO 文件的本地路径
     *
     * @param isoResource ISO 文件资源
     * @return 本地文件路径
     * @throws IOException               如果资源不存在
     * @throws IllegalArgumentException  如果文件不是本地存储的
     */
    private Path getLocalIsoPath(Resource isoResource) throws IOException {
        if (!isoResource.exists()) {
            throw new IOException("ISO 文件不存在: " + isoResource.getDescription());
        }

        try {
            // 尝试作为 PathResource 获取路径
            if (isoResource instanceof org.springframework.core.io.PathResource pathResource) {
                return Path.of(pathResource.getPath());
            }
            // 尝试通过 URI 转换
            if (isoResource.isFile()) {
                return Path.of(isoResource.getURI());
            }
        } catch (Exception e) {
            log.warn("[PXE-ISO] 获取本地路径失败: {}", e.getMessage());
        }

        throw new IllegalArgumentException("ISO 文件必须存储在本地文件系统，当前存储不支持直接访问");
    }

    /**
     * 规范化 ISO 内部路径
     */
    private String normalizePath(String pathWithinIso) {
        if (pathWithinIso == null || pathWithinIso.isEmpty()) {
            return "/";
        }
        String normalized = pathWithinIso.replace("\\", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    /**
     * 打开 ISO9660 文件系统
     */
    private FileSystem<?> openIso9660(Path isoPath) throws IOException {
        return new Iso9660FileSystem(isoPath.toFile(), true);
    }

    /**
     * 打开 UDF 文件系统
     */
    private FileSystem<?> openUdf(Path isoPath) throws IOException {
        return new UDFFileSystem(isoPath.toFile(), true);
    }

    /**
     * 从文件系统中列出指定路径下的文件
     */
    private List<String> listFilesFromFs(FileSystem<?> fs, String pathWithinIso) {
        List<String> files = new ArrayList<>();
        String targetPath = pathWithinIso.substring(1); // 移除开头的 /

        for (Object obj : fs) {
            FileEntry entry = (FileEntry) obj;
            String entryPath = entry.getPath();

            // 检查是否在目标目录下
            if (isUnderPath(entryPath, targetPath)) {
                // 获取相对路径
                String relativePath = getRelativePath(entryPath, targetPath);
                // 只添加直接子项（不包含更深层级的文件）
                if (!relativePath.contains("/")) {
                    files.add(entry.getName());
                }
            }
        }

        return files;
    }

    /**
     * 检查路径是否在目标目录下
     */
    private boolean isUnderPath(String entryPath, String targetPath) {
        if (targetPath.isEmpty() || targetPath.equals("/")) {
            // 根目录：检查是否没有父路径
            return !entryPath.contains("/");
        }
        return entryPath.startsWith(targetPath + "/") || entryPath.equals(targetPath);
    }

    /**
     * 获取相对路径
     */
    private String getRelativePath(String entryPath, String targetPath) {
        if (targetPath.isEmpty()) {
            return entryPath;
        }
        if (entryPath.startsWith(targetPath + "/")) {
            return entryPath.substring(targetPath.length() + 1);
        }
        return entryPath;
    }

    /**
     * 从 ISO9660 格式获取文件流
     */
    private CloseableResource getFileFromIso9660(Path isoPath, String normalizedPath, String originalPath) throws IOException {
        Iso9660FileSystem fs = new Iso9660FileSystem(isoPath.toFile(), true);
        try {
            return getFileFromFs(fs, normalizedPath, originalPath);
        } catch (Exception e) {
            try {
                fs.close();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    /**
     * 从 UDF 格式获取文件流
     */
    private CloseableResource getFileFromUdf(Path isoPath, String normalizedPath, String originalPath) throws IOException {
        UDFFileSystem fs = new UDFFileSystem(isoPath.toFile(), true);
        try {
            return getFileFromFs(fs, normalizedPath, originalPath);
        } catch (Exception e) {
            try {
                fs.close();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    /**
     * 从文件系统中获取文件流，以流式方式读取避免 OOM。
     * 返回的 {@link CloseableResource} 持有底层 ISO 文件系统的引用，
     * 调用方必须在使用完毕后关闭以释放文件系统资源。
     */
    private <T extends FileEntry> CloseableResource getFileFromFs(FileSystem<T> fs, String normalizedPath, String originalPath) throws IOException {
        String targetPath = normalizedPath.substring(1); // 移除开头的 /

        for (T entry : fs) {
            if (entry.getPath().equals(targetPath) || entry.getName().equals(targetPath)) {
                String fileName = Path.of(originalPath).getFileName().toString();
                long size = entry.getSize();

                @SuppressWarnings("unchecked")
                FileSystem<FileEntry> rawFs = (FileSystem<FileEntry>) fs;
                return new IsoEntryResource(rawFs, entry, fileName, originalPath, size);
            }
        }

        throw new IOException("文件不存在于 ISO 中: " + originalPath);
    }

    /**
     * ISO 文件条目资源，以流式方式读取文件内容。
     * 通过 {@link LifecycleInputStream} 将 ISO 文件系统的生命周期绑定到 InputStream，
     * 当 InputStream 被关闭时自动关闭底层 ISO 文件系统。
     */
    private static class IsoEntryResource extends AbstractResource implements CloseableResource {
        private final FileSystem<FileEntry> fileSystem;
        private final FileEntry entry;
        private final String fileName;
        private final String description;
        private final long size;

        IsoEntryResource(FileSystem<FileEntry> fileSystem, FileEntry entry, String fileName, String description, long size) {
            this.fileSystem = fileSystem;
            this.entry = entry;
            this.fileName = fileName;
            this.description = description;
            this.size = size;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new LifecycleInputStream(fileSystem.getInputStream(entry), fileSystem);
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public long contentLength() {
            return size;
        }

        @Override
        public String getDescription() {
            return "ISO entry [" + description + "]";
        }

        @Override
        public void close() throws IOException {
            fileSystem.close();
        }
    }

    /**
     * 生命周期 InputStream 包装器。
     * 关闭此流时，同时关闭底层 InputStream 和关联的 ISO 文件系统。
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
