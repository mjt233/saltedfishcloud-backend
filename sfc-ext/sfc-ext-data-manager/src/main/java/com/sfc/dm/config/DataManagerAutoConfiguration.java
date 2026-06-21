package com.sfc.dm.config;

import com.sfc.dm.controller.InvalidDataController;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.dm.service.ClaimService;
import com.sfc.dm.service.InvalidDataService;
import com.sfc.dm.service.identify.FileTypeCheckerImpl;
import com.sfc.dm.service.identify.metadata.DocumentMetadataExtractor;
import com.sfc.dm.service.identify.metadata.ExeMetadataExtractor;
import com.sfc.dm.service.identify.metadata.FileMetadataExtractor;
import com.sfc.dm.service.identify.metadata.ImageMetadataExtractor;
import com.sfc.dm.service.identify.metadata.MsiMetadataExtractor;
import com.sfc.dm.service.identify.metadata.TextMetadataExtractor;
import com.sfc.dm.service.identify.provider.ArchiveCheckProvider;
import com.sfc.dm.service.identify.provider.AudioCheckProvider;
import com.sfc.dm.service.identify.provider.IsoCheckProvider;
import com.sfc.dm.service.identify.provider.TikaFileTypeProvider;
import com.sfc.dm.service.identify.provider.VideoCheckProvider;
import com.sfc.dm.service.identify.tika.TikaServerManager;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.sfc.dm.task.typecheck.FileTypeCheckTaskFactory;
import com.sfc.dm.task.detect.InvalidDataDetectTaskFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数据管理插件自动配置
 */
@Configuration
@EntityScan(basePackageClasses = {InvalidDataRecord.class})
@EnableJpaRepositories(basePackageClasses = {InvalidDataRecordRepo.class})
@Import({
        InvalidDataController.class,
        InvalidDataService.class,
        ClaimService.class,
        FileTypeCheckerImpl.class,
        InvalidDataDetectTaskFactory.class,
        FileTypeCheckTaskFactory.class
})
public class DataManagerAutoConfiguration {

    @Configuration
    @ConditionalOnClass(name = "com.saltedfishcloud.ext.ve.core.FFMpegHelper")
    public static class FFMpegProviderConfiguration {
        /**
         * 当sfc-ext-video-enhance插件存在时，注册视频文件类型识别提供者
         */
        @Bean
        public VideoCheckProvider videoCheckProvider(FFMpegHelper ffMpegHelper) {
            return new VideoCheckProvider(ffMpegHelper);
        }

        /**
         * 当sfc-ext-video-enhance插件存在时，注册音频文件类型识别提供者
         */
        @Bean
        public AudioCheckProvider audioCheckProvider(FFMpegHelper ffMpegHelper) {
            return new AudioCheckProvider(ffMpegHelper);
        }
    }

    /**
     * 通用文件类型识别提供者配置（不依赖 FFMpeg）
     */
    @Configuration
    public static class CommonProviderConfiguration {
        /**
         * Tika Server 子进程管理器
         */
        @Bean(destroyMethod = "stop")
        public TikaServerManager tikaServerManager() {
            return new TikaServerManager();
        }

        /**
         * 基于 Tika 的统一文件类型识别提供者（文档/安装包/可执行文件/图片/纯文本）
         */
        @Bean
        public TikaFileTypeProvider tikaFileTypeProvider(TikaServerManager tikaServerManager) {
            Map<String, FileMetadataExtractor> extractors = Stream.of(
                    new DocumentMetadataExtractor(tikaServerManager),
                    new ImageMetadataExtractor(tikaServerManager),
                    new ExeMetadataExtractor(tikaServerManager),
                    new MsiMetadataExtractor(),
                    new TextMetadataExtractor()
            ).collect(Collectors.toMap(FileMetadataExtractor::getTypeId, Function.identity()));
            return new TikaFileTypeProvider(tikaServerManager, extractors);
        }

        /**
         * 注册压缩包文件类型识别提供者
         */
        @Bean
        public ArchiveCheckProvider archiveCheckProvider() {
            return new ArchiveCheckProvider();
        }

        /**
         * 注册 ISO 镜像文件类型识别提供者
         */
        @Bean
        public IsoCheckProvider isoCheckProvider() {
            return new IsoCheckProvider();
        }
    }
}
