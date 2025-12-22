package com.sfc.nwt.service;

import com.sfc.nwt.upnp.UpnpDevicesManager;
import com.sfc.nwt.upnp.UpnpUtils;
import com.sfc.nwt.upnp.model.ServiceActionInvokeParam;
import com.sfc.nwt.upnp.model.UpnpDevice;
import com.sfc.nwt.upnp.model.xml.service.av.Scpd;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Optional;

/**
 * UPnP 管理服务
 */
@RequiredArgsConstructor
public class UpnpService {
    private final UpnpDevicesManager upnpDevicesManager;

    /**
     * 对 UPnP 的服务发起调用
     * @return 接口响应原始报文
     */
    public UpnpUtils.SimpleHttpResponse invokeUpnpService(ServiceActionInvokeParam param) throws IOException {
        UpnpDevice upnpDevice = Optional.ofNullable(upnpDevicesManager.getByRootUSN(param.getRootDeviceUSN()))
                .orElseThrow(() -> new IllegalArgumentException("root device " + param.getRootDeviceUSN() + " not found"));
        return UpnpUtils.invokeService(upnpDevice, param);
    }


    /**
     * 获取 UPnP 设备的服务描述参数
     * @param usn   根设备UDN/USN
     * @param serviceType   服务类型
     */
    public Scpd getUpnpServiceScpd(String usn, String serviceType) throws IOException {
        UpnpDevice upnpDevice = Optional.ofNullable(upnpDevicesManager.getByRootUSN(usn)).orElseThrow(() -> new IllegalArgumentException("root device " + usn + " not found"));
        return UpnpUtils.getServiceScpd(upnpDevice, serviceType);
    }
}
