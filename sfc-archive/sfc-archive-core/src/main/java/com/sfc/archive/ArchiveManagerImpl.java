package com.sfc.archive;

import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.composer.impl.ZipArchiveCompressor;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.extractor.impl.ZipArchiveExtractor;
import com.sfc.archive.model.ArchiveParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;


@Slf4j
public class ArchiveManagerImpl implements ArchiveManager {
    @Override
    public ArchiveCompressor getCompressor(ArchiveParam param, OutputStream outputStream) {
        if ("zip".equals(param.getType())) {
            return new ZipArchiveCompressor(outputStream, param.getEncoding());
        } else {
            throw new IllegalArgumentException("目前仅支持zip");
        }
    }

    @Override
    public ArchiveExtractor getExtractor(ArchiveParam param, Resource resource) {
        if ("zip".equals(param.getType())) {
            if (resource instanceof PathResource) {
                try {
                    return new ZipArchiveExtractor(resource.getFile());
                } catch (IOException e) {
                    log.error("获取解压器出错", e);
                    throw new RuntimeException(e);
                }
            } else {
                throw new IllegalArgumentException("目前仅支持本地文件资源");
            }
        } else {
            throw new IllegalArgumentException("目前仅支持zip");
        }
    }
}
