package com.xiaotao.saltedfishcloud.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@PropertySource("classpath:config.properties")
public class FtpConfig {
    public static int FTP_PORT = 21;

    public static String PASSIVE_ADDR = null;

    public static String PASSIVE_PORT = null;

    @Value("${ftp-passive-port}")
    public void setPassivePort(String port) {
        log.info("[FTP]被动模式传输端口:" + port);
        PASSIVE_PORT = port;
    }

    @Value("${ftp-passive-addr}")
    public void setPassiveAddr(String addr) {
        log.info("[FTP]被动模式地址:" + addr);
        PASSIVE_ADDR = addr;
    }

    @Value("${ftp-port}")
    public void setFtpPort(int port) {
        log.info("[FTP]服务控制端口:" + port);
        FTP_PORT = port;
    }
}
