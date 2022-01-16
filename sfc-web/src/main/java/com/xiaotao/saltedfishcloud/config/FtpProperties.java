package com.xiaotao.saltedfishcloud.config;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * FTP服务配置信息类
 * @TODO 启动时优先从数据库加载配置
 */
@Slf4j
@Component
@Data
public class FtpProperties {
    /**
     * 是否启用FTP服务
     */
    @Value("${ftp-enable}")
    private boolean ftpEnable;

    /**
     * FTP控制监听地址
     */
    @Value("${ftp-listen}")
    private String listenAddr;

    /**
     * 主控制端口
     */
    @Value("${ftp-port}")
    private int controlPort = 21;

    /**
     * 被动传输地址
     */
    @Value("${ftp-passive-addr}")
    private String passiveAddr = null;

    /**
     * 被动传输端口范围
     */
    @Value("${ftp-passive-port}")
    private String passivePort = null;
}
