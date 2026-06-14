package com.sfc.dm.config;

import com.sfc.dm.controller.InvalidDataController;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.dm.service.ClaimService;
import com.sfc.dm.service.InvalidDataService;
import com.sfc.dm.service.identify.FileTypeCheckerImpl;
import com.sfc.dm.service.identify.AudioCheckProvider;
import com.sfc.dm.service.identify.ImageCheckProvider;
import com.sfc.dm.service.identify.VideoCheckProvider;
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
}
