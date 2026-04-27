package com.sfc.archive.engine.zip4j;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.AbstractArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * 基于 zip4j 的 ZIP 引擎，支持加密压缩与解压。
 */
public class Zip4jArchiveEngineProvider extends AbstractArchiveEngineProvider {
    @Override
    public String getId() {
        return "zip4j";
    }

    @Override
    public String getName() {
        return "Zip4j";
    }

    @Override
    public boolean supportEncrypt() {
        return true;
    }

    @Override
    public boolean supportDecrypt() {
        return true;
    }

    @Override
    public Collection<String> getSupportedCompressExtensions() {
        return Collections.singleton(".zip");
    }

    @Override
    public Collection<String> getSupportedDecompressExtensions() {
        return Collections.singleton(".zip");
    }

    @Override
    public ArchiveEngineCompressor createCompressor(OutputStream outputStream, ArchiveEngineProperty property) throws IOException {
        return new Zip4jArchiveEngineCompressor(outputStream, normalizeProperty(property));
    }

    @Override
    public ArchiveEngineDecompressor createDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException {
        return new Zip4jArchiveEngineDecompressor(resource, normalizeProperty(property));
    }
}



