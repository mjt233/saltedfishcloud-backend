package com.sfc.pxeboot.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * PXE 服务状态信息
 */
@Data
@Builder
public class PxeServiceStatus {

    /**
     * TFTP 服务是否运行中
     */
    private boolean tftpRunning;

    /**
     * HTTP 服务是否运行中
     */
    private boolean httpRunning;

    /**
     * ProxyDHCP 服务是否运行中
     */
    private boolean proxyDhcpRunning;

    /**
     * TFTP 服务端口
     */
    private int tftpPort;

    /**
     * HTTP 服务端口
     */
    private int httpPort;

    /**
     * 活跃启动项数量
     */
    private int activeBootItems;

    /**
     * 活跃会话数量
     */
    private int activeSessions;

    /**
     * 最后错误信息
     */
    private String lastError;
}
