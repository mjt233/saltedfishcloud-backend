package com.sfc.archive.engine.support;

import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.ArchiveResource;
import org.apache.commons.compress.archivers.ArchiveException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 旧版 ArchiveExtractor 到新版 ArchiveEngineDecompressor 的适配器。
 */
public class LegacyArchiveEngineDecompressorAdapter implements ArchiveEngineDecompressor {
    /**
     * 旧版解压器。
     */
    private final ArchiveExtractor archiveExtractor;

    /**
     * 创建解压适配器。
     *
     * @param archiveExtractor 旧版解压器
     */
    public LegacyArchiveEngineDecompressorAdapter(ArchiveExtractor archiveExtractor) {
        this.archiveExtractor = archiveExtractor;
    }

    @Override
    public Iterator<ArchiveResource> getArchiveResources() throws IOException {
        try {
            List<? extends ArchiveFile> files = archiveExtractor.listFiles();
            List<ArchiveResource> resources = new ArrayList<>(files.size());
            for (ArchiveFile file : files) {
                resources.add(EngineResourceUtils.toArchiveResource(file));
            }
            return resources.iterator();
        } catch (ArchiveException e) {
            throw new IOException("读取压缩包资源列表失败", e);
        }
    }

    @Override
    public InputStream getInputStream(String archivePath) throws IOException {
        try {
            return archiveExtractor.getInputStream(stripPrefixSlash(archivePath));
        } catch (ArchiveException e) {
            throw new IOException("读取压缩包资源流失败: " + archivePath, e);
        }
    }

    @Override
    public void close() throws IOException {
        archiveExtractor.close();
    }

    /**
     * 去掉路径前导斜杠，以兼容旧接口的文件查找逻辑。
     *
     * @param path 路径
     * @return 去除前导斜杠后的路径
     */
    private String stripPrefixSlash(String path) {
        if (path == null) {
            return null;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }
}


