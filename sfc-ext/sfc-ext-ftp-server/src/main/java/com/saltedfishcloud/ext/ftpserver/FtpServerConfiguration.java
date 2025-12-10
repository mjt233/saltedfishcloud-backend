package com.saltedfishcloud.ext.ftpserver;

import com.saltedfishcloud.ext.ftpserver.core.DiskFtpFileSystemFactory;
import com.saltedfishcloud.ext.ftpserver.core.DiskFtpUserManager;
import com.saltedfishcloud.ext.ftpserver.ftplet.FtpUploadAndLogHandler;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FtpServerConfiguration {
    @Autowired
    private ConfigService configService;
    @Bean
    public FtpService ftpService() {
        return new FtpServiceImpl();
    }

    @Bean
    public DiskFtpUserManager diskFtpUserManager() {
        return new DiskFtpUserManager();
    }

    @Bean
    public DiskFtpFileSystemFactory diskFtpFileSystemFactory() {
        return new DiskFtpFileSystemFactory();
    }

    @Bean
    public FtpUploadAndLogHandler ftpUploadHandler() {
        return new FtpUploadAndLogHandler();
    }

    @Bean
    public FTPServerProperty ftpServerProperty() {
        FTPServerProperty property = new FTPServerProperty();
        configService.bindPropertyEntity(property);
        return property;
    }
}
