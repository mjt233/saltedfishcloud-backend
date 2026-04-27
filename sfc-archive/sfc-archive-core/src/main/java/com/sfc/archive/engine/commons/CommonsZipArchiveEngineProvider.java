package com.sfc.archive.engine.commons;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.AbstractArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;

/**
 * 基于 Apache Commons Compress 的 ZIP 引擎实现。
 */
public class CommonsZipArchiveEngineProvider extends AbstractArchiveEngineProvider {
    @Override
    public String getId() {
        return "apache-commons-compress";
    }

    @Override
    public String getName() {
        return "Apache Commons Compress";
    }

    @Override
    public Collection<String> getSupportedCompressExtensions() {
        return Arrays.asList(".zip");
    }

    @Override
    public Collection<String> getSupportedDecompressExtensions() {
        return Arrays.asList(".zip", ".7z");
    }

    @Override
    public ArchiveEngineCompressor createCompressor(OutputStream outputStream, ArchiveEngineProperty property) throws IOException {
        ArchiveEngineProperty normalized = normalizeProperty(property);

        // 目前只实现了对zip的支持，就不做分支判断了
        matchDecompressorExtension(property, null);
        return new CommonsZipStreamArchiveEngineCompressor(outputStream, normalized);
    }

    @Override
    public ArchiveEngineDecompressor createDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException {
        ArchiveEngineProperty normalized = normalizeProperty(property);
        String extension = matchCompressorExtension(property, resource.getFilename());
        if (".7z".equals(extension)) {
            return new SevenZArchiveEngineDecompressor(resource, normalized);
        }
        if (".zip".equals(extension)) {
            return new CommonsZipArchiveEngineDecompressor(resource, normalized);
        }
        // 不支持的格式 matchCompressorExtension 已抛出了异常，一般不会走到这里，除非在 getSupportedCompressExtensions 声明了支持格式，但未在该方法中实现。
        throw new UnsupportedOperationException();
    }
}

