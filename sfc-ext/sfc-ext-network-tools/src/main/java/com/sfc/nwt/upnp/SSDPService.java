package com.sfc.nwt.upnp;

import com.sfc.nwt.upnp.constants.UpnpConstants;
import com.sfc.nwt.upnp.event.SSDPEventListener;

import java.io.IOException;

/**
 * SSDP 简单服务发现服务，持续发送SSDP请求和接收响应。
 */
public interface SSDPService {

    /**
     * 添加一个接收 SSDP 消息的监听器
     */
    void addEventListener(SSDPEventListener listener);

    /**
     * 移除一个接收 SSDP 消息的监听器
     */
    void removeEventListener(SSDPEventListener listener);

    /**
     * 停止发送SSDP搜索组播消息，停止监听SSDP响应
     */
    void stop();

    /**
     * 发送 SSDP 搜索消息
     *
     * @param st 搜索的设备类型，可参考预定义的常量{@link UpnpConstants.SsdpType}
     */
    void doSearch(String st) throws IOException;

    /**
     * 发送SSDP搜索组播消息，并开始监听SSDP响应。允许重复调用，重复调用尝试重新加入所有接口的 SSDP 组播消息订阅
     */
    void start() throws IOException;
}
