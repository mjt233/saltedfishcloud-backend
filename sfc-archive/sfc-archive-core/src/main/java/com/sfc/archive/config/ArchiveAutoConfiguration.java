package com.sfc.archive.config;

import com.sfc.archive.ArchiveManager;
import com.sfc.archive.ArchiveManagerImpl;
import com.sfc.archive.ArchiveEngineProvider;
import com.sfc.archive.composer.impl.zip.ZipArchiveCompressorProvider;
import com.sfc.archive.engine.commons.CommonsZipArchiveEngineProvider;
import com.sfc.archive.engine.rar.RarArchiveEngineProvider;
import com.sfc.archive.engine.sevenz.SevenZArchiveEngineProvider;
import com.sfc.archive.engine.zip4j.Zip4jArchiveEngineProvider;
import com.sfc.archive.extractor.impl.zip.ZipArchiveExtractorProvider;
import com.sfc.archive.service.DiskFileSystemArchiveService;
import com.sfc.archive.service.DiskFileSystemArchiveServiceImpl;
import com.sfc.archive.task.ArchiveExtractAsyncTaskFactory;
import com.sfc.archive.task.CompressAsyncTaskFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        DiskFileSystemArchiveServiceImpl.class,
        CompressAsyncTaskFactory.class,
        ArchiveExtractAsyncTaskFactory.class
})
public class ArchiveAutoConfiguration {
    @Bean
    public ArchiveManager archiveManager(ObjectProvider<ArchiveEngineProvider> engineProviders) {
        ArchiveManagerImpl archiveManager = new ArchiveManagerImpl();
        // 注册压缩器
        archiveManager.registerCompressor(new ZipArchiveCompressorProvider());

        // 注册解压器
        archiveManager.registerExtractor(new ZipArchiveExtractorProvider());

        // 注册新引擎
        engineProviders.orderedStream().forEach(archiveManager::registerEngineProvider);

        return archiveManager;
    }

    /**
     * commons-zip 引擎。
     * @return 引擎提供者
     */
    @Bean
    public ArchiveEngineProvider commonsZipArchiveEngineProvider() {
        return new CommonsZipArchiveEngineProvider();
    }

    /**
     * zip4j 引擎。
     * @return 引擎提供者
     */
    @Bean
    public ArchiveEngineProvider zip4jArchiveEngineProvider() {
        return new Zip4jArchiveEngineProvider();
    }

    /**
     * 7z 解压引擎。
     * @return 引擎提供者
     */
    @Bean
    public ArchiveEngineProvider sevenZArchiveEngineProvider() {
        return new SevenZArchiveEngineProvider();
    }

    /**
     * rar 解压引擎。
     * @return 引擎提供者
     */
    @Bean
    public ArchiveEngineProvider rarArchiveEngineProvider() {
        return new RarArchiveEngineProvider();
    }
}
