package com.sfc.archive.engine.rar;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.AbstractArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.EncryptionCapability;
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
    public String getId() {
        return "junrar";
    }

    @Override
    public String getName() {
        return "Junrar";
    }

    /**
     * 获取 Junrar 对 .rar 的加密能力声明。
     *
     * @return 支持的加密能力集合
     */
    @Override
    public Collection<EncryptionCapability> getSupportedEncryptionCapabilities() {
        return Collections.singletonList(
                EncryptionCapability.builder()
                        .extension(".rar")
                        .operation(EncryptionCapability.EncryptionOperation.DECOMPRESS)
                        .build()
        );
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

