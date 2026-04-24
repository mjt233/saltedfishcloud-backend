package com.sfc.archive.engine.sevenz;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.AbstractArchiveEngineProvider;
import com.sfc.archive.model.ArchiveProperty;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * 7z 解压引擎提供者。
 */
public class SevenZArchiveEngineProvider extends AbstractArchiveEngineProvider {
    @Override
    public String getId() {
        return "commons-7z";
    }

    @Override
    public String getName() {
        return "Apache Commons 7z Engine";
    }

    @Override
    public Collection<String> getSupportedCompressExtensions() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getSupportedDecompressExtensions() {
        return Collections.singleton(".7z");
    }

    @Override
    public ArchiveEngineCompressor createCompressor(OutputStream outputStream, ArchiveProperty property) {
        throw new JsonException("commons-7z 暂不支持压缩");
    }

    @Override
    public ArchiveEngineDecompressor createDecompressor(Resource resource, ArchiveProperty property) throws IOException {
        return new SevenZArchiveEngineDecompressor(resource, normalizeProperty(property));
    }
}

