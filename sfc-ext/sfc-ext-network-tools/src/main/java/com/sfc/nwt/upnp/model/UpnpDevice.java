package com.sfc.nwt.upnp.model;

import com.sfc.nwt.upnp.model.xml.UpnpDescribe;
import lombok.Data;

/**
 * Upnp 设备信息
 */
@Data
public class UpnpDevice {
    /**
     * 发现/刷新上线时间
     */
    private long foundAt;

    /**
     * 缓存过期的时间
     */
    private long expireAt;

    /**
     * 缓存保留时长（秒）
     */
    private int cacheControlMaxAge;

    /**
     * 描述 URL
     */
    private String location;

    /**
     * 描述信息
     */
    private UpnpDescribe describe;
}
