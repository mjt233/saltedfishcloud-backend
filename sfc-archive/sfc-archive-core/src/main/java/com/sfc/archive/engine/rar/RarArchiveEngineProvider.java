package com.sfc.archive.engine.rar;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.AbstractArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * RAR 解压引擎提供者。
 */
public class RarArchiveEngineProvider extends AbstractArchiveEngineProvider {
    @Override
    public boolean supportDecrypt() {
        return true;
    }

    @Override
    public String getId() {
        return "junrar";
    }

    @Override
    public String getName() {
        return "Junrar";
    }

    @Override
    public Collection<String> getSupportedCompressExtensions() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getSupportedDecompressExtensions() {
        return Collections.singleton(".rar");
    }

    @Override
    public ArchiveEngineCompressor createCompressor(OutputStream outputStream, ArchiveEngineProperty property) {
        throw new JsonException("junrar 暂不支持压缩");
    }

    @Override
    public ArchiveEngineDecompressor createDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException {
        return new RarArchiveEngineDecompressor(resource, normalizeProperty(property));
    }
}

