package com.sfc.archive.engine.commons;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.AbstractArchiveEngineProvider;
import com.sfc.archive.engine.support.LegacyArchiveEngineDecompressorAdapter;
import com.sfc.archive.extractor.impl.zip.ZipArchiveExtractor;
import com.sfc.archive.model.ArchiveParam;
import com.sfc.archive.model.ArchiveProperty;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * 基于 Apache Commons Compress 的 ZIP 引擎实现。
 */
public class CommonsZipArchiveEngineProvider extends AbstractArchiveEngineProvider {
    @Override
    public String getId() {
        return "commons-zip";
    }

    @Override
    public String getName() {
        return "Apache Commons ZIP Engine";
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
    public ArchiveEngineCompressor createCompressor(OutputStream outputStream, ArchiveProperty property) throws IOException {
        ArchiveProperty normalized = normalizeProperty(property);
        if (normalized.getEncryptionParam() != null) {
            throw new JsonException("commons-zip 不支持加密压缩，请切换 zip4j 引擎");
        }
        return new CommonsZipStreamArchiveEngineCompressor(outputStream, normalized);
    }

    @Override
    public ArchiveEngineDecompressor createDecompressor(Resource resource, ArchiveProperty property) {
        ArchiveProperty normalized = normalizeProperty(property);
        ArchiveParam archiveParam = ArchiveParam.builder()
                .type("zip")
                .encoding(normalized.getEncoding())
                .password(normalized.getEncryptionParam() == null ? null : normalized.getEncryptionParam().getPassword())
                .build();
        return new LegacyArchiveEngineDecompressorAdapter(new ZipArchiveExtractor(archiveParam, resource));
    }
}

