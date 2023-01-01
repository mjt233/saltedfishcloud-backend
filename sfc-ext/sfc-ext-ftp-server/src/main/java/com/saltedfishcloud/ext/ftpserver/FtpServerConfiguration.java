package com.saltedfishcloud.ext.ftpserver;

import com.saltedfishcloud.ext.ftpserver.core.DiskFtpFileSystemFactory;
import com.saltedfishcloud.ext.ftpserver.core.DiskFtpUserManager;
import com.saltedfishcloud.ext.ftpserver.ftplet.FtpUploadHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FtpServerConfiguration {
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
    public FtpUploadHandler ftpUploadHandler() {
        return new FtpUploadHandler();
    }
}
