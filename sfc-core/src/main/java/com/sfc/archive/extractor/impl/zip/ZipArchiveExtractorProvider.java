package com.sfc.archive.extractor.impl.zip;

import com.sfc.archive.ArchiveExtractorProvider;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveParam;
import org.springframework.core.io.Resource;

import java.util.Collection;
import java.util.Collections;

public class ZipArchiveExtractorProvider implements ArchiveExtractorProvider {
    @Override
    public ArchiveExtractor getExtractor(ArchiveParam param, Resource resource) {
        return new ZipArchiveExtractor(param, resource);
    }

    @Override
    public Collection<String> getSupportsType() {
        return Collections.singleton("zip");
    }
}
