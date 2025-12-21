package com.sfc.nwt.upnp;

import com.sfc.nwt.upnp.constants.UpnpConstants;
import com.sfc.nwt.upnp.model.xml.UpnpDescribe;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.experimental.UtilityClass;

import java.io.StringReader;
import java.util.Optional;

import static com.sfc.nwt.upnp.constants.UpnpConstants.Network.MULTICAST_GROUP;
import static com.sfc.nwt.upnp.constants.UpnpConstants.Network.MULTICAST_PORT;

@UtilityClass
public class UpnpUtils {

    /**
     * 构建标准的SSDP搜索消息，搜索所有设备
     */
    public static String buildSSDPSearchMessage() {
        return buildSSDPSearchMessage(UpnpConstants.SsdpType.ALL);
    }

    /**
     * 构建标准的SSDP搜索消息
     * @param st 搜索的设备类型，可参考预定义的常量{@link UpnpConstants.SsdpType}
     */
    public static String buildSSDPSearchMessage(String st) {
        String appVersion = Optional.ofNullable(SpringContextUtils.getContext())
                .map(c -> c.getBean(SysProperties.class))
                .map(SysProperties::getVersion)
                .map(Object::toString)
                .orElse("3.0.0");
        return "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: " + MULTICAST_GROUP + ":" + MULTICAST_PORT + "\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: " + st + "\r\n\r\n" +
                "USER-AGENT: Saltedfishcloud/" + appVersion + " " + (OSInfo.isWindows() ? "Windows" : "Linux/Unix") + "\r\n";
    }

    /**
     * 解析 Upnp 根设备的xml描述
     */
    public static UpnpDescribe parseRootDesc(String xmlDesc) {
        try {
            UpnpDescribe res = (UpnpDescribe) JAXBContext.newInstance(UpnpDescribe.class)
                    .createUnmarshaller()
                    .unmarshal(new StringReader(xmlDesc));
            res.setRawXML(xmlDesc);
            return res;
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }
}
