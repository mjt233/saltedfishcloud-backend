package com.sfc.archive.engine.sevenz;

import com.sfc.archive.engine.AbstractArchiveEngineDecompressor;
import com.sfc.archive.utils.EngineResourceUtils;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.springframework.core.io.Resource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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
        List<ArchiveResource> resources = new ArrayList<>();
        try (SevenZFile sevenZFile = openSevenZFile()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                resources.add(ArchiveResource.builder()
                        .name(extractName(entry.getName()))
                        .archivePath(EngineResourceUtils.normalizeArchivePath(entry.getName()))
                        .isDirectory(entry.isDirectory())
                        .size(entry.isDirectory() ? 0L : entry.getSize())
                        .lastModified(entry.getLastModifiedDate() == null ? null : new Date(entry.getLastModifiedDate().getTime()))
                        .created(entry.getCreationDate() == null ? null : new Date(entry.getCreationDate().getTime()))
                        .build());
            }
        }
        return resources.iterator();
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
        localFileResource.cleanup();
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

