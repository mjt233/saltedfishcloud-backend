package com.sfc.archive.engine.zip4j;

import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.utils.EngineResourceUtils;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * zip4j 解压执行器。
 */
public class Zip4jArchiveEngineDecompressor implements ArchiveEngineDecompressor {
    /**
     * 本地文件引用。
     */
    private final EngineResourceUtils.LocalFileResource localFileResource;

    /**
     * zip4j 文件对象。
     */
    private final ZipFile zipFile;

    /**
     * 创建 zip4j 解压器。
     *
     * @param resource 待解压资源
     * @param property 属性
     * @throws IOException 初始化失败
     */
    public Zip4jArchiveEngineDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException {
        this.localFileResource = EngineResourceUtils.toLocalFile(resource, ".zip");
        String password = property.getEncryptionParam() == null ? null : property.getEncryptionParam().getPassword();
        this.zipFile = password == null
                ? new ZipFile(localFileResource.getFile())
                : new ZipFile(localFileResource.getFile(), password.toCharArray());
    }

    @Override
    public Iterator<ArchiveResource> getArchiveResources() throws IOException {
        List<FileHeader> headers = zipFile.getFileHeaders();
        List<ArchiveResource> resources = new ArrayList<>(headers.size());
        for (FileHeader header : headers) {
            String fileName = header.getFileName();
            resources.add(ArchiveResource.builder()
                    .name(extractName(fileName))
                    .size(header.isDirectory() ? 0L : header.getUncompressedSize())
                    .archivePath(EngineResourceUtils.normalizeArchivePath(fileName))
                    .isDirectory(header.isDirectory())
                    .lastModified(header.getLastModifiedTime() > 0 ? new Date(header.getLastModifiedTimeEpoch()) : null)
                    .build());
        }
        return resources.iterator();
    }

    @Override
    public InputStream getInputStream(String archivePath) throws IOException {
        FileHeader fileHeader = zipFile.getFileHeader(stripPrefixSlash(archivePath));
        if (fileHeader == null || fileHeader.isDirectory()) {
            throw new JsonException("压缩包内资源不存在: " + archivePath);
        }
        return zipFile.getInputStream(fileHeader);
    }

    @Override
    public void close() throws IOException {
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
     * 从路径提取文件名。
     *
     * @param path 路径
     * @return 文件名
     */
    private String extractName(String path) {
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == path.length() - 1) {
            return path;
        }
        return path.substring(slashIndex + 1);
    }
}


