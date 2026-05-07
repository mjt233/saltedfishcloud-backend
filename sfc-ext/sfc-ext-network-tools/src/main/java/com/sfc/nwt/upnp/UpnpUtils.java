package com.sfc.nwt.upnp;

import com.sfc.nwt.upnp.constants.UpnpConstants;
import com.sfc.nwt.upnp.model.ServiceActionInvokeParam;
import com.sfc.nwt.upnp.model.UpnpDevice;
import com.sfc.nwt.upnp.model.xml.device.UpnpDescribe;
import com.sfc.nwt.upnp.model.xml.service.av.Scpd;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.model.Pair;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.UtilityClass;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

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
        return "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: " + MULTICAST_GROUP + ":" + MULTICAST_PORT + "\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: " + st + "\r\n\r\n" +
                "USER-AGENT: " + getUserAgent() + "\r\n";
    }

    /**
     * 获取本程序的USER-AGENT值
     */
    public static String getUserAgent() {
        String appVersion = Optional.ofNullable(SpringContextUtils.getContext())
                .map(c -> c.getBean(SysProperties.class))
                .map(SysProperties::getVersion)
                .map(Object::toString)
                .orElse("3.0.0");
        return "Saltedfishcloud/" + appVersion + " " + (OSInfo.isWindows() ? "Windows" : "Linux");
    }

    /**
     * 获取 UPnP 设备的服务描述
     * @param device    UPnP 设备
     * @param serviceType   服务类型
     */
    public static Scpd getServiceScpd(UpnpDevice device, String serviceType) throws IOException {

        UpnpDescribe.Service service = Optional.ofNullable(UpnpUtils.findService(device.getDescribe(), s -> Objects.equals(serviceType, s.getServiceType())))
                .orElseThrow(() -> new IllegalArgumentException("serviceType " + serviceType + " is not supported"));

        URL scpdUrl = Optional.ofNullable(service.getSCPDURL())
                .map(url -> UpnpUtils.getServiceOrDeviceUrl(device.getLocation(), url))
                .orElseThrow(() -> new IllegalArgumentException("SCPDURL not found in this service describe"));

        return getXMLByUrl(scpdUrl, Scpd.class);
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static class SimpleHttpResponse {
        private int statusCode;
        private List<Pair<String, String>> headers;
        private String responseBody;
    }

    /**
     * 对 UPnP 设备发起服务调用
     * @param upnpDevice    目标设备
     * @param param 服务调用参数
     */
    public static SimpleHttpResponse invokeService(UpnpDevice upnpDevice, ServiceActionInvokeParam param) throws IOException {
        String serviceType = param.getServiceType();

        // 构造请求 URL
        UpnpDescribe.Service service = Optional.ofNullable(UpnpUtils.findService(upnpDevice.getDescribe(), s -> Objects.equals(serviceType, s.getServiceType())))
                .orElseThrow(() -> new IllegalArgumentException("serviceType " + serviceType + " is not supported"));

        // 构造请求体报文
        byte[] requestBody = buildServiceActionRequestBody(serviceType, param.getAction(), param.getActionParams()).getBytes();
        URL url = getServiceOrDeviceUrl(upnpDevice.getLocation(), service.getControlURL());

        // 发起 HTTP 请求
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        connection.setRequestProperty("Soapaction", "\"" + param.getServiceType() + "#" + param.getAction() + "\"");
        connection.setRequestProperty("Connection", "Close");
        connection.setDoOutput(true);

        // 发送请求
        try (OutputStream os = connection.getOutputStream()) {
            StreamUtils.copy(requestBody, os);
        }
        InputStream is;

        // 接收响应
        try{
            is = connection.getInputStream();
        } catch (IOException e) {
            InputStream es = connection.getErrorStream();
            if (es == null) {
                throw e;
            }
            is = es;
        }

        try(InputStream responseInputStream = is) {
            return SimpleHttpResponse.builder()
                    .statusCode(connection.getResponseCode())
                    .headers(connection.getHeaderFields()
                            .entrySet()
                            .stream()
                            .flatMap(entry -> entry
                                    .getValue()
                                    .stream()
                                    .map(value -> new Pair<>(entry.getKey(), value))
                            )
                            .toList()
                    )
                    .responseBody(StreamUtils.copyToString(responseInputStream, StandardCharsets.UTF_8))
                    .build();
        }
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

    /**
     * 从 UPnP 设备描述中获取某个服务描述
     * @param upnpDescribe  UPnP 设备描述
     * @param predicate 匹配函数
     * @return  匹配成功返回该服务，没有匹配的返回null
     */
    public static UpnpDescribe.Service findService(UpnpDescribe upnpDescribe, Predicate<UpnpDescribe.Service> predicate) {
        ArrayDeque<UpnpDescribe.Device> deviceList = new ArrayDeque<>();
        deviceList.push(upnpDescribe.getDevice());
        while (!deviceList.isEmpty()) {
            UpnpDescribe.Device device = deviceList.pop();
            List<UpnpDescribe.Service> serviceList = device.getServiceList();
            if (serviceList != null) {
                Optional<UpnpDescribe.Service> service = serviceList.stream().filter(predicate).findAny();
                if (service.isPresent()) {
                    return service.get();
                }
            }
            Optional.ofNullable(device.getDeviceList())
                    .ifPresent(deviceList::addAll);
        }
        return null;
    }

    /**
     * 获取 UPnP 设备的子设备或服务的完整URL
     * @param locationUrl   UPnP设备描述location URL
     * @param serviceOrDeviceUrl    服务或设备声明的 URL
     */
    public static URL getServiceOrDeviceUrl(String locationUrl, String serviceOrDeviceUrl) {

        String url;
        try {
            if (serviceOrDeviceUrl.startsWith("http://") || serviceOrDeviceUrl.startsWith("https://")) {
                return new URL(serviceOrDeviceUrl);
            } else {
                url = URLUtils.getBaseUrl(locationUrl) + (serviceOrDeviceUrl.startsWith("/") ? "" : "/") + serviceOrDeviceUrl;
            }
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 根据 URL 一口气读取目标资源的所有数据
     */
    public static byte[] getContentByUrl(String url) throws IOException {
        return getContentByUrl(new URL(url));
    }

    /**
     * 根据 URL 一口气读取目标资源的所有数据
     */
    public static byte[] getContentByUrl(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(2000);
        try(InputStream is = connection.getInputStream()) {
            return StreamUtils.copyToByteArray(is);
        } finally {
            if (connection instanceof HttpURLConnection httpURLConnection) {
                httpURLConnection.disconnect();
            }
        }
    }

    /**
     * 访问URL获取XML内容，并反序列化为Java对象
     */
    public static <T> T getXMLByUrl(String url, Class<T> clazz) throws IOException {
        return getXMLByUrl(new URL(url), clazz);
    }

    /**
     * 访问URL获取XML内容，并反序列化为Java对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T getXMLByUrl(URL url, Class<T> clazz) throws IOException {
        try {
            String xmlContent = new String(getContentByUrl(url), StandardCharsets.UTF_8);
            return (T) JAXBContext.newInstance(clazz)
                    .createUnmarshaller()
                    .unmarshal(new StringReader(xmlContent));
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 构造服务调用请求体
     * @param serviceType 服务类型
     * @param action    要调用服务的动作
     * @param params    执行参数
     */
    public static String buildServiceActionRequestBody(String serviceType, String action, List<Pair<String, String>> params) {
        Objects.requireNonNull(serviceType, "serviceType is null");
        Objects.requireNonNull(action, "action is null");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>")
                .append("<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">")
                .append("<s:Body>")
                .append("<u:").append(action).append(" xmlns:u=\"").append(serviceType).append("\">");

        if (params != null) {
            for (Pair<String, String> pair : params) {
                sb.append("<").append(pair.getKey()).append(">")
                        .append(pair.getValue())
                        .append("</").append(pair.getKey()).append(">");
            }
        }

        sb.append("</u:").append(action).append("></s:Body></s:Envelope>");
        return sb.toString();
    }
}
