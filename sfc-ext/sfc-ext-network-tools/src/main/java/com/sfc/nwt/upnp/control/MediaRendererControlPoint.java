package com.sfc.nwt.upnp.control;

import com.sfc.nwt.upnp.UpnpUtils;
import com.sfc.nwt.upnp.constants.UpnpConstants;
import com.sfc.nwt.upnp.model.ServiceActionInvokeParam;
import com.sfc.nwt.upnp.model.UpnpDevice;
import com.sfc.nwt.upnp.model.xml.device.UpnpDescribe;
import com.sfc.nwt.upnp.model.xml.service.av.Scpd;
import com.xiaotao.saltedfishcloud.model.Pair;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UPnP 多媒体设备控制点
 */
public class MediaRendererControlPoint {
    private UpnpDevice upnpDevice;
    private UpnpDescribe.Service atvTransportService;
    private UpnpDescribe.Service renderingControlService;
    private UpnpDescribe.Service connectionManagerService;

    private Scpd scpd;


    protected Scpd loadScpd() throws IOException {
        if (this.scpd == null) {
            URL url = UpnpUtils.getServiceOrDeviceUrl(upnpDevice.getLocation(), atvTransportService.getSCPDURL());
            this.scpd = UpnpUtils.getXMLByUrl(url, Scpd.class);
        }
        return this.scpd;
    }

    /**
     * 调用 AVTransport 服务
     * @param action 执行的动作
     * @param params 参数
     */
    protected UpnpUtils.SimpleHttpResponse invokeAVTransportService(String action, List<Pair<String, String>> params) throws IOException {
        return UpnpUtils.invokeService(upnpDevice, ServiceActionInvokeParam.builder()
                .serviceType(atvTransportService.getServiceType())
                .rootDeviceUSN(upnpDevice.getDescribe().getDevice().getUdn())
                .action(action)
                .actionParams(params)
                .build());
    }

    /**
     * 推送多媒体投屏
     *
     * @param uri 媒体 URI
     * @param instanceId 目标实例 id。一般情况给null或0即可
     */
    public void castMedia(String instanceId, String uri) throws IOException {
        String instanceIdValue = Optional.ofNullable(instanceId).filter(StringUtils::hasText).orElse(getInstanceId());

        // 先停止正在进行的播放
        this.stop(instanceIdValue);

        // 设置要播放的 URI
        UpnpUtils.SimpleHttpResponse setURIRes = this.setAVTransportURI(instanceIdValue, uri);
        if (setURIRes.getStatusCode() != 200) {
            throw new RuntimeException(setURIRes.getResponseBody());
        }

        // 开始播放
        this.play(instanceIdValue);
    }

    /**
     * 设置待播放的视频URI
     * @param uri 媒体 URI
     * @param instanceId 目标实例 id。一般情况给null或0即可
     */
    public UpnpUtils.SimpleHttpResponse setAVTransportURI(String instanceId, String uri) throws IOException {
        String instanceIdValue = Optional.ofNullable(instanceId).filter(StringUtils::hasText).orElse(getInstanceId());
        return this.invokeAVTransportService("SetAVTransportURI", List.of(
                new Pair<>("InstanceID", instanceIdValue),
                new Pair<>("CurrentURI", uri),
                new Pair<>("CurrentURIMetaData", "")
        ));
    }

    /**
     * 暂停媒体播放
     */
    public UpnpUtils.SimpleHttpResponse pause(String instanceId) throws IOException {
        String instanceIdValue = Optional.ofNullable(instanceId).filter(StringUtils::hasText).orElse(getInstanceId());
        return this.invokeAVTransportService("Pause", List.of( new Pair<>("InstanceID", instanceIdValue) ));
    }

    /**
     * 停止媒体播放
     */
    public UpnpUtils.SimpleHttpResponse stop(String instanceId) throws IOException {
        String instanceIdValue = Optional.ofNullable(instanceId).filter(StringUtils::hasText).orElse(getInstanceId());
        return this.invokeAVTransportService("Stop", List.of( new Pair<>("InstanceID", instanceIdValue) ));
    }

    /**
     * 执行播放
     */
    public UpnpUtils.SimpleHttpResponse play(String instanceId) throws IOException {
        String instanceIdValue = Optional.ofNullable(instanceId).filter(StringUtils::hasText).orElse(getInstanceId());
        // 播放
        return this.invokeAVTransportService("Play", List.of(
                new Pair<>("InstanceID", instanceIdValue),
                new Pair<>("Speed", "1")
        ));
    }

    /**
     * 执行播放
     */
    public UpnpUtils.SimpleHttpResponse seek(String instanceId) throws IOException {
        String instanceIdValue = Optional.ofNullable(instanceId).filter(StringUtils::hasText).orElse(getInstanceId());
        // 播放
        return this.invokeAVTransportService("Play", List.of(
                new Pair<>("InstanceID", instanceIdValue),
                new Pair<>("Speed", "1")
        ));
    }

    /**
     * 获取设备支持的媒体协议信息
     */
    public List<String> getProtocolInfo() throws IOException {
        UpnpUtils.SimpleHttpResponse resp = UpnpUtils.invokeService(
                upnpDevice,
                ServiceActionInvokeParam.builder()
                        .serviceType("urn:schemas-upnp-org:service:ConnectionManager:1")
                        .action("GetProtocolInfo")
                        .rootDeviceUSN(upnpDevice.getDescribe().getDevice().getUdn())
                        .build()
        );
        if (resp.getStatusCode() != 200) {
            throw new RuntimeException(resp.getResponseBody());
        }
        try (ByteArrayInputStream is = new ByteArrayInputStream(resp.getResponseBody().getBytes())) {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            StringBuilder sb = new StringBuilder();
            parser.parse(is, new DefaultHandler() {
                boolean isInTag;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    if ("Sink".equals(qName) || qName.endsWith(":Sink")) {
                        isInTag = true;
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) throws SAXException {
                    if (isInTag) {
                        sb.append(ch, start, length);
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) throws SAXException {
                    if ("Sink".equals(qName) || qName.endsWith(":Sink")) {
                        isInTag = false;
                    }
                }
            });
            return Arrays.stream(sb.toString().split(",")).filter(s -> !s.isEmpty()).toList();
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取设备可用的服务实例id
     */
    protected String getInstanceId() throws IOException {
        return this.loadScpd().getServiceStateTable()
                        .stream()
                        .filter(t -> "InstanceID".equals(t.getName()))
                        .findAny()
                        .map(Scpd.StateVariable::getDefaultValue)
                        .orElse("0");
    }

    /**
     * 创建一个UPnP媒体播放器控制点实例
     *
     * @param device 媒体播放器UPnP设备
     */
    public MediaRendererControlPoint(UpnpDevice device) {
        Objects.requireNonNull(device, "device is null");
        if (!UpnpConstants.DeviceType.MEDIA_RENDERER.equals(device.getDescribe().getDevice().getDeviceType())) {
            throw new IllegalArgumentException(device.getDescribe().getDevice().getDeviceType() + " is not a media renderer");
        }
        this.upnpDevice = device;
        this.atvTransportService = Optional.ofNullable(UpnpUtils.findService(device.getDescribe(), s -> UpnpConstants.ServiceType.AV_TRANSPORT.equals(s.getServiceType())))
                .orElseThrow(() -> new IllegalArgumentException("device is not exist ATVTransport service"));
        this.renderingControlService = UpnpUtils.findService(device.getDescribe(), s -> UpnpConstants.ServiceType.RENDERING_CONTROL.equals(s.getServiceType()));
        this.connectionManagerService = UpnpUtils.findService(device.getDescribe(), s -> UpnpConstants.ServiceType.CONNECTION_MANAGER.equals(s.getServiceType()));
    }
}
