package com.sfc.nwt.upnp.event;

import com.sfc.nwt.upnp.model.SsdpMessage;
import lombok.Getter;

import java.net.InetAddress;

@Getter
public class SSDPEvent<T extends SsdpMessage> {
    /**
     * 原始 ssdp 响应报文
     */
    private String rawResponse;

    /**
     * 消息实体类
     */
    private T message;

    /**
     * 消息来源地址
     */
    private InetAddress address;

    public SSDPEvent(String rawResponse, T message, InetAddress address) {
        this.rawResponse = rawResponse;
        this.message = message;
        this.address = address;
    }
}
