package com.sfc.nwt.upnp.model.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import java.util.List;

/**
 * UPnP 设备描述文档根元素
 * <p>对应 XML 中的 {@code <root>} 元素，包含设备的完整描述信息</p>
 * <p>命名空间: urn:schemas-upnp-org:device-1-0</p>
 */
@Data
@XmlRootElement(name = "root")
@XmlAccessorType(XmlAccessType.FIELD)
public class UpnpDescribe {

    /**
     * 原始 XML
     */
    private String rawXML;

    /**
     * UPnP 协议版本信息
     * <p>对应 {@code <specVersion>} 元素，描述设备遵循的 UPnP 协议版本</p>
     */
    private SpecVersion specVersion;

    /**
     * 设备描述信息
     * <p>对应 {@code <device>} 元素，包含设备的所有详细信息和嵌套设备结构</p>
     */
    private Device device;

    /**
     * UPnP 协议版本规格
     * <p>定义设备支持的 UPnP 协议主要和次要版本号</p>
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SpecVersion {
        /**
         * 主版本号
         * <p>对应 {@code <major>} 元素，通常为 1</p>
         */
        private int major;

        /**
         * 次版本号
         * <p>对应 {@code <minor>} 元素，通常为 0 或 1</p>
         */
        private int minor;
    }

    /**
     * UPnP 设备实体
     * <p>描述一个 UPnP 设备的所有属性和能力</p>
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Device {
        /**
         * 设备类型标识符
         * <p>对应 {@code <deviceType>} 元素，URN 格式的设备类型定义</p>
         * <p>例如: urn:schemas-upnp-org:device:MediaRenderer:1</p>
         */
        private String deviceType;

        /**
         * 设备友好名称
         * <p>对应 {@code <friendlyName>} 元素，用户可读的设备显示名称</p>
         * <p>例如: "客厅电视"、"卧室音响" 等</p>
         */
        private String friendlyName;

        /**
         * 设备型号名称
         * <p>对应 {@code <modelName>} 元素，设备的型号名称</p>
         * <p>例如: "Windows Digital Media Renderer"、"TV-X1000" 等</p>
         */
        private String modelName;

        /**
         * 设备型号编号
         */
        private String moduleNumber;

        /**
         * 设备型号页面
         */
        private String modelURL;

        /**
         * 唯一设备名称 (Universal Device Name)
         * <p>对应 {@code <UDN>} 元素，设备的全局唯一标识符</p>
         * <p>格式: uuid:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx</p>
         */
        @XmlElement(name = "UDN")
        private String udn;

        /**
         * 设备图标列表
         * <p>对应 {@code <iconList>} 包装元素，包含多个 {@code <icon>} 元素</p>
         * <p>用于在不同界面展示设备图标，支持多种尺寸和格式</p>
         */
        @XmlElementWrapper(name = "iconList")
        @XmlElement(name = "icon")
        private List<Icon> iconList;

        /**
         * 设备服务列表
         * <p>对应 {@code <serviceList>} 包装元素，包含多个 {@code <service>} 元素</p>
         * <p>描述设备提供的 UPnP 服务，如 AVTransport、RenderingControl 等</p>
         */
        @XmlElementWrapper(name = "serviceList")
        @XmlElement(name = "service")
        private List<Service> serviceList;

        /**
         * 嵌套设备列表
         * <p>对应 {@code <deviceList>} 包装元素，包含多个嵌套的 {@code <device>} 元素</p>
         * <p>用于描述复合设备中的子设备，支持递归结构</p>
         * <p>例如: 路由器包含 WAN 设备，WAN 设备包含 WAN 连接设备</p>
         */
        @XmlElementWrapper(name = "deviceList")
        @XmlElement(name = "device")
        private List<Device> deviceList;

        /**
         * 设备制造商名称
         * <p>对应 {@code <manufacturer>} 元素，设备的生产厂商</p>
         * <p>例如: "Microsoft Corporation"、"TP-LINK" 等</p>
         */
        private String manufacturer;

        /**
         * 制造商官方网站 URL
         * <p>对应 {@code <manufacturerURL>} 元素，制造商的官方网站地址</p>
         */
        private String manufacturerURL;

        /**
         * 设备型号描述
         * <p>对应 {@code <modelDescription>} 元素，设备的详细描述信息</p>
         * <p>例如: "Digital Media Renderer"、"Wireless Router" 等</p>
         */
        private String modelDescription;

        /**
         * 设备型号编号
         * <p>对应 {@code <modelNumber>} 元素，设备的型号版本号</p>
         * <p>例如: "1.0"、"7.0" 等</p>
         */
        private String modelNumber;

        /**
         * 设备管理页面 URL
         * <p>对应 {@code <presentationURL>} 元素，设备 Web 管理界面的访问地址</p>
         * <p>通常用于路由器的配置页面</p>
         */
        private String presentationURL;
    }

    /**
     * 设备图标信息
     * <p>描述设备图标的属性，支持多种格式和尺寸</p>
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Icon {
        /**
         * 图标 MIME 类型
         * <p>对应 {@code <mimetype>} 元素，图标的媒体类型</p>
         * <p>例如: "image/jpeg"、"image/png"</p>
         */
        private String mimetype;

        /**
         * 图标宽度（像素）
         * <p>对应 {@code <width>} 元素，图标的水平像素数</p>
         * <p>常见值: 48, 120, 256 等</p>
         */
        private int width;

        /**
         * 图标高度（像素）
         * <p>对应 {@code <height>} 元素，图标的垂直像素数</p>
         * <p>常见值: 48, 120, 256 等</p>
         */
        private int height;

        /**
         * 图标颜色深度（位）
         * <p>对应 {@code <depth>} 元素，每个像素的颜色位数</p>
         * <p>常见值: 24 (真彩色), 32 (带透明度) 等</p>
         */
        private int depth;

        /**
         * 图标资源 URL
         * <p>对应 {@code <url>} 元素，图标文件的访问地址</p>
         * <p>相对路径或绝对 URL，可从设备获取图标文件</p>
         */
        private String url;
    }

    /**
     * UPnP 服务描述
     * <p>描述设备提供的一个具体 UPnP 服务</p>
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Service {
        /**
         * 服务类型标识符
         * <p>对应 {@code <serviceType>} 元素，URN 格式的服务类型定义</p>
         * <p>例如: urn:schemas-upnp-org:service:AVTransport:1</p>
         */
        private String serviceType;

        /**
         * 服务标识符
         * <p>对应 {@code <serviceId>} 元素，服务的唯一标识符</p>
         * <p>格式: urn:upnp-org:serviceId:服务名称</p>
         */
        private String serviceId;

        /**
         * 服务控制 URL
         * <p>对应 {@code <controlURL>} 元素，用于发送 SOAP 控制命令的地址</p>
         * <p>客户端通过此 URL 调用服务的各种操作</p>
         */
        private String controlURL;

        /**
         * 事件订阅 URL
         * <p>对应 {@code <eventSubURL>} 元素，用于订阅服务状态变化的地址</p>
         * <p>客户端通过此 URL 注册接收服务状态更新通知</p>
         */
        private String eventSubURL;
    }
}