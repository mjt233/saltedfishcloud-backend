package com.xiaotao.saltedfishcloud.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FTP服务配置信息类
 */
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
