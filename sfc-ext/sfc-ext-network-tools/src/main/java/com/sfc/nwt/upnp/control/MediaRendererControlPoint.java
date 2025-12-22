package com.sfc.nwt.upnp.control;

import com.sfc.nwt.upnp.UpnpUtils;
import com.sfc.nwt.upnp.constants.UpnpConstants;
import com.sfc.nwt.upnp.model.UpnpDevice;
import com.sfc.nwt.upnp.model.xml.device.UpnpDescribe;
import com.sfc.nwt.upnp.model.xml.service.av.Scpd;
import com.xiaotao.saltedfishcloud.model.Pair;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * UPnP 多媒体设备控制点
 */
public class MediaRendererControlPoint {
    private final UpnpDevice upnpDevice;
    private final UpnpDescribe.Service atvTransportService;
    private final UpnpDescribe.Service renderingControlService;

    private Scpd scpd;

    private MediaRendererControlPoint(UpnpDevice upnpDevice, UpnpDescribe.Service atvTransportService, UpnpDescribe.Service renderingControlService) {
        this.upnpDevice = upnpDevice;
        this.atvTransportService = atvTransportService;
        this.renderingControlService = renderingControlService;
    }


    protected Scpd loadScpd() throws IOException {
        if (this.scpd == null) {
            URL url = UpnpUtils.getServiceOrDeviceUrl(upnpDevice.getLocation(), atvTransportService.getSCPDURL());
            this.scpd = UpnpUtils.getXMLByUrl(url, Scpd.class);
        }
        return this.scpd;
    }

    /**
     * 推送多媒体投屏
     * @param url   媒体URL
     */
    protected void castMedia(String url) {

    }

    /**
     * 获取设备可用的服务实例id
     */
    protected int getInstanceId() throws IOException {
        return Integer.parseInt(
                this.loadScpd().getServiceStateTable()
                        .stream()
                        .filter(t -> "InstanceID".equals(t.getName()))
                        .findAny()
                        .map(Scpd.StateVariable::getDefaultValue)
                        .orElse("0")
        );
    }

    /**
     * 创建一个UPnP媒体播放器控制点实例
     * @param device    媒体播放器UPnP设备
     */
    public static MediaRendererControlPoint newInstant(UpnpDevice device) {
        if(UpnpConstants.DeviceType.MEDIA_RENDERER.equals(device.getDescribe().getDevice().getDeviceType())) {
            throw new IllegalArgumentException(device.getDescribe().getDevice().getDeviceType() + " is not a media renderer");
        }

        UpnpDescribe.Service atvService = UpnpUtils.findService(device.getDescribe(), s -> UpnpConstants.ServiceType.AVTransport.equals(s.getServiceType()));
        if (atvService == null) {
            throw new IllegalArgumentException("device is not exist ATVTransport service");
        }
        UpnpDescribe.Service controlService = UpnpUtils.findService(device.getDescribe(), s -> UpnpConstants.ServiceType.RENDERING_CONTROL.equals(s.getServiceType()));
        return new MediaRendererControlPoint(device, atvService, controlService);
    }
}
