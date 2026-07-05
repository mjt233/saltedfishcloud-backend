package com.sfc.pxeboot.server.iso;

import com.sfc.pxeboot.PxeBootProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * ISO 处理器。
 * <p>将 Spring {@link Resource} 转换为本地文件后委托给 {@link IsoFileSystem}。
 * 根据 {@link PxeBootProperty#getIsoEngine()} 配置动态选择 ISO 解析引擎。</p>
 */
@Slf4j
public class JavaIsoHandler implements IsoHandler {

    private final PxeBootProperty pxeBootProperty;

    /**
     * 构造函数。
     *
     * @param pxeBootProperty PXE 启动配置
     */
    public JavaIsoHandler(PxeBootProperty pxeBootProperty) {
        this.pxeBootProperty = pxeBootProperty;
    }

    @Override
    public List<String> listFiles(Resource isoResource, String pathWithinIso) throws IOException {
        IsoFileSystem fs = createFileSystem(isoResource);
        return fs.listFiles(pathWithinIso).stream()
            .map(IsoFileEntry::getName)
            .toList();
    }

    @Override
    public Resource getResource(Resource isoResource, String pathWithinIso) throws IOException {
        IsoFileSystem fs = createFileSystem(isoResource);
        return fs.getResource(pathWithinIso);
    }

    @Override
    public List<IsoFileEntry> findFilesByPattern(Resource isoResource, String pattern, String basePath) throws IOException {
        IsoFileSystem fs = createFileSystem(isoResource);
        return fs.findFilesByPattern(pattern, basePath);
    }

    /**
     * 根据配置创建对应的 IsoFileSystem 实现。
     *
     * @param isoResource ISO 文件资源
     * @return IsoFileSystem 实例
     * @throws IOException 如果资源不存在或不是本地文件
     */
    private IsoFileSystem createFileSystem(Resource isoResource) throws IOException {
        File isoFile = getLocalIsoFile(isoResource);
        String engine = pxeBootProperty.getIsoEngine();

        if ("sevenzipjbinding".equalsIgnoreCase(engine)) {
            log.debug("[PXE-ISO] 使用 SevenZipJBinding 引擎: {}", isoFile);
            return new SevenZipJBindingIsoFileSystem(isoFile);
        }

        log.debug("[PXE-ISO] 使用 java-iso-tools 引擎: {}", isoFile);
        return new JavaIsoToolsIso9660FileSystem(isoFile);
    }

    /**
     * 从 Resource 获取 ISO 文件的本地 File 对象。
     *
     * @param isoResource ISO 文件资源
     * @return 本地文件
     * @throws IOException              如果资源不存在
     * @throws IllegalArgumentException 如果文件不是本地存储的
     */
    private File getLocalIsoFile(Resource isoResource) throws IOException {
        if (!isoResource.exists()) {
            throw new IOException("ISO 文件不存在: " + isoResource.getDescription());
        }

        try {
            if (isoResource instanceof PathResource pathResource) {
                return Path.of(pathResource.getPath()).toFile();
            }
            if (isoResource.isFile()) {
                return Path.of(isoResource.getURI()).toFile();
            }
        } catch (Exception e) {
            log.warn("[PXE-ISO] 获取本地路径失败: {}", e.getMessage());
        }

        throw new IllegalArgumentException("ISO 文件必须存储在本地文件系统，当前存储不支持直接访问");
    }
}
