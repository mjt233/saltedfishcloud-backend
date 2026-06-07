package com.sfc.pxeboot.model.dto;

import lombok.Data;

/**
 * PXE 会话信息
 */
@Data
public class PxeSessionInfo {

    /**
     * 客户端 IP 地址
     */
    private String clientIp;

    /**
     * 最后请求的路径
     */
    private String lastRequestPath;

    /**
     * 最后活跃时间戳
     */
    private long lastActiveTime;

    /**
     * 总传输字节数
     */
    private long totalBytesTransferred;

    /**
     * 请求次数
     */
    private long requestCount;
}
