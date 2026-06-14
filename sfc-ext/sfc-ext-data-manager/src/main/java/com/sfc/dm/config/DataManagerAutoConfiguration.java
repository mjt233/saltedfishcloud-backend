package com.sfc.dm.config;

import com.sfc.dm.controller.InvalidDataController;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.dm.service.ClaimService;
import com.sfc.dm.service.InvalidDataService;
import com.sfc.dm.service.identify.FileTypeCheckerImpl;
import com.sfc.dm.service.identify.provider.ArchiveCheckProvider;
import com.sfc.dm.service.identify.provider.AudioCheckProvider;
import com.sfc.dm.service.identify.provider.DocumentCheckProvider;
import com.sfc.dm.service.identify.provider.ExeCheckProvider;
import com.sfc.dm.service.identify.provider.ImageCheckProvider;
import com.sfc.dm.service.identify.provider.IsoCheckProvider;
import com.sfc.dm.service.identify.provider.MsiCheckProvider;
import com.sfc.dm.service.identify.provider.PlainTextCheckProvider;
import com.sfc.dm.service.identify.provider.VideoCheckProvider;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.sfc.dm.task.typecheck.FileTypeCheckTaskFactory;
import com.sfc.dm.task.detect.InvalidDataDetectTaskFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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

        /**
         * 当sfc-ext-video-enhance插件存在时，注册图片文件类型识别提供者
         */
        @Bean
        public ImageCheckProvider imageCheckProvider(FFMpegHelper ffMpegHelper) {
            return new ImageCheckProvider(ffMpegHelper);
        }
    }

    /**
     * 通用文件类型识别提供者配置（不依赖 FFMpeg）
     */
    @Configuration
    public static class CommonProviderConfiguration {
        /**
         * 注册文档文件类型识别提供者
         */
        @Bean
        public DocumentCheckProvider documentCheckProvider() {
            return new DocumentCheckProvider();
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

        /**
         * 注册纯文本文件类型识别提供者
         */
        @Bean
        public PlainTextCheckProvider plainTextCheckProvider() {
            return new PlainTextCheckProvider();
        }

        /**
         * 注册可执行文件类型识别提供者
         */
        @Bean
        public ExeCheckProvider exeCheckProvider() {
            return new ExeCheckProvider();
        }

        /**
         * 注册 MSI 安装包文件类型识别提供者
         */
        @Bean
        public MsiCheckProvider msiCheckProvider() {
            return new MsiCheckProvider();
        }
    }
}
