package com.sfc.archive;

import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class ArchiveManagerImpl implements ArchiveManager {
    private final Map<String, ArchiveCompressorProvider> compressorMap = new ConcurrentHashMap<>();
    private final Map<String, ArchiveExtractorProvider> extractorMap = new ConcurrentHashMap<>();


    @Override
    public ArchiveCompressor getCompressor(ArchiveParam param, OutputStream outputStream) {
        ArchiveCompressorProvider compressorProvider = compressorMap.get(param.getType().toLowerCase());
        if (compressorProvider != null) {
            return compressorProvider.getCompressor(param, outputStream);
        } else {
            throw new IllegalArgumentException("不支持压缩的类型: " + param.getType());
        }
    }

    @Override
    public ArchiveExtractor getExtractor(ArchiveParam param, Resource resource) {
        ArchiveExtractorProvider extractorProvider = extractorMap.get(param.getType().toLowerCase());
        if (extractorProvider != null) {
            return extractorProvider.getExtractor(param, resource);
        } else {
            throw new IllegalArgumentException("不支持解压的类型: " + param.getType());
        }
    }

    @Override
    public void registerCompressor(ArchiveCompressorProvider provider) {
        for (String type : provider.getSupportsType()) {
            ArchiveCompressorProvider exist = compressorMap.putIfAbsent(type.toLowerCase(), provider);
            if (exist != null) {
                log.warn("压缩器类型冲突: {}，提供者将被忽略: {}", type, provider);
            }
        }
    }

    @Override
    public void registerExtractor(ArchiveExtractorProvider provider) {
        for (String type : provider.getSupportsType()) {
            ArchiveExtractorProvider exist = extractorMap.putIfAbsent(type.toLowerCase(), provider);
            if (exist != null) {
                log.warn("解压缩器类型冲突: {}，提供者将被忽略: {}", type, provider);
            }
        }
    }
}
