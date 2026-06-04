package com.sfc.pxeboot.server.iso;

import com.github.stephenc.javaisotools.loopfs.api.FileEntry;
import com.github.stephenc.javaisotools.loopfs.api.FileSystem;
import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileSystem;
import com.github.stephenc.javaisotools.loopfs.udf.UDFFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Java ISO 处理器
 * 使用 java-iso-tools 支持 ISO9660 和 UDF 格式
 */
@Slf4j
public class JavaIsoHandler implements IsoHandler {

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Override
    public List<String> listFiles(Long uid, String isoPath, String isoFileName, String pathWithinIso) throws IOException {
        Path isoLocalPath = getLocalIsoPath(uid, isoPath, isoFileName);
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
    public Resource getFileStream(Long uid, String isoPath, String isoFileName, String pathWithinIso) throws IOException {
        Path isoLocalPath = getLocalIsoPath(uid, isoPath, isoFileName);
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
     * 获取 ISO 文件的本地路径
     *
     * @param uid         用户 ID
     * @param isoPath     ISO 文件所在目录路径
     * @param isoFileName ISO 文件名
     * @return 本地文件路径
     * @throws IOException          如果文件不存在
     * @throws IllegalArgumentException 如果文件不是本地存储的
     */
    private Path getLocalIsoPath(Long uid, String isoPath, String isoFileName) throws IOException {
        Resource resource = diskFileSystemManager.getMainFileSystem().getResource(uid, isoPath, isoFileName);

        if (resource == null) {
            throw new IOException("ISO 文件不存在: " + isoPath + "/" + isoFileName);
        }

        try {
            // 尝试作为 PathResource 获取路径
            if (resource instanceof org.springframework.core.io.PathResource) {
                String pathStr = ((org.springframework.core.io.PathResource) resource).getPath();
                return Path.of(pathStr);
            }
            // 尝试通过 URI 转换
            if (resource.isFile()) {
                return Path.of(resource.getURI());
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
    private Resource getFileFromIso9660(Path isoPath, String normalizedPath, String originalPath) throws IOException {
        Iso9660FileSystem fs = new Iso9660FileSystem(isoPath.toFile(), true);
        return getFileFromFs(fs, normalizedPath, originalPath);
    }

    /**
     * 从 UDF 格式获取文件流
     */
    private Resource getFileFromUdf(Path isoPath, String normalizedPath, String originalPath) throws IOException {
        UDFFileSystem fs = new UDFFileSystem(isoPath.toFile(), true);
        return getFileFromFs(fs, normalizedPath, originalPath);
    }

    /**
     * 从文件系统中获取文件流
     */
    @SuppressWarnings("unchecked")
    private <T extends FileEntry> Resource getFileFromFs(FileSystem<T> fs, String normalizedPath, String originalPath) throws IOException {
        String targetPath = normalizedPath.substring(1); // 移除开头的 /

        for (T entry : fs) {
            if (entry.getPath().equals(targetPath) || entry.getName().equals(targetPath)) {
                InputStream is = fs.getInputStream(entry);
                String fileName = Path.of(originalPath).getFileName().toString();

                return new InputStreamResource(is) {
                    @Override
                    public String getFilename() {
                        return fileName;
                    }

                    @Override
                    public long contentLength() {
                        return entry.getSize();
                    }
                };
            }
        }

        throw new IOException("文件不存在于 ISO 中: " + originalPath);
    }
}
