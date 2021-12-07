package com.xiaotao.saltedfishcloud.compress;

import com.xiaotao.saltedfishcloud.compress.enums.ArchiveType;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.OutputStream;

public class ArchiveFactoryBuilder {
    private final static CompressorStreamFactory FACTORY = new CompressorStreamFactory();
    public ArchiveFactoryBuilder(ArchiveType type, OutputStream output) throws CompressorException {
        
    }
}
