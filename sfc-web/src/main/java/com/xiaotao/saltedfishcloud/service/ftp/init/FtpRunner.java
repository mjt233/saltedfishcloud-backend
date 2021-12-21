package com.xiaotao.saltedfishcloud.service.ftp.init;

import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.FtpServer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.annotation.Resource;

@Configuration
@Slf4j
@Order(5)
public class FtpRunner implements ApplicationRunner {
    @Resource
    private FtpServer ftpServer;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        ftpServer.start();
        log.info("[FTP]服务已启动");
    }
}
