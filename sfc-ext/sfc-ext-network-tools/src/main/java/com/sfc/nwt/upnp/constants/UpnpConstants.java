package com.sfc.nwt.upnp.constants;

public interface UpnpConstants {
    interface Network {
        // UPnP 使用的组播地址和端口
        String MULTICAST_GROUP = "239.255.255.250";
        int MULTICAST_PORT = 1900;
        int TIMEOUT = 5000; // 5秒超时
    }

    interface SsdpType {
        /**
         * 所有设备
         */
        String ALL = "ssdp:all";

        /**
         * 根设备
         */
        String ROOT_DEVICES = "upnp:rootdevice";
    }
}
