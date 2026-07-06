package com.sfc.ext.download.config;

import com.sfc.ext.download.DownloadService;
import com.sfc.ext.download.controller.DownloadController;
import com.sfc.ext.download.model.po.DownloadTaskInfo;
import com.sfc.ext.download.repo.DownloadTaskRepo;
import com.sfc.ext.download.service.DownloadServiceImpl;
import com.sfc.ext.download.task.DownloadAsyncTaskFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackageClasses = DownloadTaskInfo.class)
@EnableJpaRepositories(basePackageClasses = DownloadTaskRepo.class)
@Import({
        DownloadAsyncTaskFactory.class,
        DownloadServiceImpl.class,
        DownloadController.class
})
public class DownloadAutoConfiguration {
}
