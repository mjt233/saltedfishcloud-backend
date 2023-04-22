package com.sfc.archive.composer.impl.zip;

import com.sfc.archive.ArchiveCompressorProvider;
import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.model.ArchiveParam;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;

public class ZipArchiveCompressorProvider implements ArchiveCompressorProvider {
    @Override
    public ArchiveCompressor getCompressor(ArchiveParam param, OutputStream outputStream) {
        return new ZipArchiveCompressor(param, outputStream);
    }

    @Override
    public Collection<String> getSupportsType() {
        return Collections.singleton("zip");
    }
}
