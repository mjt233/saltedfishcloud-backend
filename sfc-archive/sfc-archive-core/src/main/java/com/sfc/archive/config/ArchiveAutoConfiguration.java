package com.sfc.archive.config;

import com.sfc.archive.*;
import com.sfc.archive.composer.impl.zip.ZipArchiveCompressorProvider;
import com.sfc.archive.controller.ArchiveController;
import com.sfc.archive.engine.commons.ApacheCommonsCompressEngineProvider;
import com.sfc.archive.engine.rar.RarArchiveEngineProvider;
import com.sfc.archive.engine.zip4j.Zip4jArchiveEngineProvider;
import com.sfc.archive.extractor.impl.zip.ZipArchiveExtractorProvider;
import com.sfc.archive.service.DiskFileSystemArchiveServiceImpl;
import com.sfc.archive.task.ArchiveExtractAsyncTaskFactory;
import com.sfc.archive.task.CompressAsyncTaskFactory;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        // 接口服务
        DiskFileSystemArchiveServiceImpl.class,
        CompressAsyncTaskFactory.class,
        ArchiveExtractAsyncTaskFactory.class,
        ArchiveController.class,

        // 压缩引擎提供者
        ApacheCommonsCompressEngineProvider.class,
        Zip4jArchiveEngineProvider.class,
        RarArchiveEngineProvider.class
})
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

    @Bean
    public ArchiveEngineManager archiveEngineManager(ObjectProvider<ArchiveEngineProvider> engineProviders) {
        ArchiveEngineManagerImpl engineManager = new ArchiveEngineManagerImpl();
        engineProviders.orderedStream().forEach(engineManager::registerEngineProvider);
        return engineManager;
    }

    /**
     * 注册归档引擎动态特性。
     * @param archiveEngineManager 归档引擎管理器
     * @return 特性提供者
     */
    @Bean
    public FeatureProvider archiveEngineFeatureProvider(ArchiveEngineManager archiveEngineManager) {
        return new ArchiveEngineFeatureProvider(archiveEngineManager);
    }
}
