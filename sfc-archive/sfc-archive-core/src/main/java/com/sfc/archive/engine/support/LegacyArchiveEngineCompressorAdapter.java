package com.sfc.archive.engine.support;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.comporessor.ArchiveResourceEntry;
import com.sfc.archive.model.ArchiveResource;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.util.Date;

/**
 * 旧版 ArchiveCompressor 到新版 ArchiveEngineCompressor 的适配器。
 *
 * <p>该适配器在 close 阶段统一触发旧版 start，适用于批量构建式压缩器。</p>
 */
public class LegacyArchiveEngineCompressorAdapter implements ArchiveEngineCompressor {
    /**
     * 旧版压缩器。
     */
    private final ArchiveCompressor archiveCompressor;

    /**
     * 创建压缩适配器。
     *
     * @param archiveCompressor 旧版压缩器
     */
    public LegacyArchiveEngineCompressorAdapter(ArchiveCompressor archiveCompressor) {
        this.archiveCompressor = archiveCompressor;
    }

    @Override
    public void addArchiveResource(ArchiveResource resource) {
        String path = EngineResourceUtils.normalizeArchivePath(resource.getArchivePath());
        InputStreamSource source = resource.getResource();
        ArchiveResourceEntry entry = new ArchiveResourceEntry(path.substring(1), resource.getSize() == null ? 0L : resource.getSize(), source);
        Date created = resource.getCreated();
        Date modified = resource.getLastModified();
        if (created != null) {
            entry.setCtime(created.getTime());
        }
        if (modified != null) {
            entry.setMtime(modified.getTime());
        }
        archiveCompressor.addFile(entry);
    }

    @Override
    public void close() throws IOException {
        try {
            archiveCompressor.start();
        } finally {
            archiveCompressor.close();
        }
    }
}

