package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.service.ftp.FtpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
@Slf4j
public class FtpRunner implements ApplicationRunner {
    @Resource
    private FtpService ftpService;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        ftpService.getServer().start();
        log.info("FTP服务已启动");
    }
}
