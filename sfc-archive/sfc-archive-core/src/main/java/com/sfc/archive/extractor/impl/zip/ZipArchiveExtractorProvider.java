package com.sfc.archive.extractor.impl.zip;

import com.sfc.archive.ArchiveExtractorProvider;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Collection;

public class ZipArchiveExtractorProvider implements ArchiveExtractorProvider {
    @Override
    public ArchiveExtractor getExtractor(ArchiveParam param, Resource resource) {
        if (resource instanceof PathResource) {
            try {
                return new ZipArchiveExtractor(param, resource.getFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("zip解压仅支持本地文件资源");
        }
    }

    @Override
    public Collection<String> getSupportsType() {
        return null;
    }
}
