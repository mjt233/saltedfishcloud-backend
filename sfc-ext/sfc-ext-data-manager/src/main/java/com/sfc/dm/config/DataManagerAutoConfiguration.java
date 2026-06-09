package com.sfc.dm.config;

import com.sfc.dm.controller.DataManagerController;
import com.sfc.dm.service.DataExportService;
import com.sfc.dm.service.DataImportService;
import com.sfc.dm.service.FileMetadataBackupService;
import org.springframework.context.annotation.Bean;

/**
 * 数据管理插件自动配置
 */
public class DataManagerAutoConfiguration {

    @Bean
    public DataManagerController dataManagerController() {
        return new DataManagerController();
    }

    @Bean
    public DataExportService dataExportService() {
        return new DataExportService();
    }

    @Bean
    public DataImportService dataImportService() {
        return new DataImportService();
    }

    @Bean
    public FileMetadataBackupService fileMetadataBackupService() {
        return new FileMetadataBackupService();
    }
}
