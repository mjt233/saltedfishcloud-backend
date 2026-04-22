package com.sfc.archive.engine.rar;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.support.EngineResourceUtils;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * RAR 解压执行器。
 */
public class RarArchiveEngineDecompressor implements ArchiveEngineDecompressor {
    /**
     * 本地文件引用。
     */
    private final EngineResourceUtils.LocalFileResource localFileResource;

    /**
     * rar 归档对象。
     */
    private final Archive archive;

    /**
     * 创建 RAR 解压器。
     *
     * @param resource 待解压资源
     * @param property 解压属性
     * @throws IOException 初始化失败
     */
    public RarArchiveEngineDecompressor(Resource resource, ArchiveProperty property) throws IOException {
        this.localFileResource = EngineResourceUtils.toLocalFile(resource, ".rar");
        String password = property.getEncryptionParam() == null ? null : property.getEncryptionParam().getPassword();
        try {
            this.archive = password == null ? new Archive(localFileResource.getFile()) : new Archive(localFileResource.getFile(), password);
        } catch (RarException e) {
            localFileResource.cleanup();
            throw new IOException("RAR 读取失败", e);
        }
    }

    @Override
    public Iterator<ArchiveResource> getArchiveResources() {
        List<FileHeader> headers = archive.getFileHeaders();
        List<ArchiveResource> resources = new ArrayList<>(headers.size());
        for (FileHeader header : headers) {
            String fileName = normalizePath(header.getFileNameString());
            resources.add(ArchiveResource.builder()
                    .name(extractName(fileName))
                    .size(header.isDirectory() ? 0L : header.getFullUnpackSize())
                    .archivePath(EngineResourceUtils.normalizeArchivePath(fileName))
                    .isDirectory(header.isDirectory())
                    .lastModified(header.getMTime() == null ? null : new Date(header.getMTime().getTime()))
                    .build());
        }
        return resources.iterator();
    }

    @Override
    public InputStream getInputStream(String archivePath) throws IOException {
        String normalized = stripPrefixSlash(archivePath);
        for (FileHeader header : archive.getFileHeaders()) {
            String fileName = normalizePath(header.getFileNameString());
            if (!header.isDirectory() && normalized.equals(fileName)) {
                return archive.getInputStream(header);
            }
        }
        throw new JsonException("压缩包内资源不存在: " + archivePath);
    }

    @Override
    public void close() throws IOException {
        archive.close();
        localFileResource.cleanup();
    }

    /**
     * 去掉路径前导斜杠。
     *
     * @param path 压缩包路径
     * @return 去掉前导斜杠后的路径
     */
    private String stripPrefixSlash(String path) {
        if (path == null || path.isEmpty()) {
            throw new JsonException("archivePath 不能为空");
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * 统一 RAR 条目路径分隔符。
     *
     * @param path 原始路径
     * @return 标准化路径
     */
    private String normalizePath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    /**
     * 从路径提取文件名。
     *
     * @param path 路径
     * @return 文件名
     */
    private String extractName(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == path.length() - 1) {
            return path;
        }
        return path.substring(slashIndex + 1);
    }
}


