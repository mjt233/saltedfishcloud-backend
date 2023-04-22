package com.sfc.archive.config;

import com.sfc.archive.ArchiveManager;
import com.sfc.archive.ArchiveManagerImpl;
import com.sfc.archive.composer.impl.zip.ZipArchiveCompressorProvider;
import com.sfc.archive.extractor.impl.zip.ZipArchiveExtractorProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArchiveAutoConfiguration {
    @Bean
    public ArchiveManager archiveManager() {
        ArchiveManagerImpl archiveManager = new ArchiveManagerImpl();
        // 注册压缩器
        archiveManager.registerCompressor(new ZipArchiveCompressorProvider());

        // 注册解压器
        archiveManager.registerExtractor(new ZipArchiveExtractorProvider());

        return archiveManager;
    }
}
