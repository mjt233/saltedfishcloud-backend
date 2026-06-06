package com.sfc.pxeboot;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import lombok.Data;

/**
 * PXE 网络启动配置属性
 */
@Data
@ConfigPropertyEntity(prefix = "pxe-boot", groups = {
    @ConfigPropertiesGroup(id = "basic", name = "基础配置"),
    @ConfigPropertiesGroup(id = "tftp", name = "TFTP 服务配置"),
    @ConfigPropertiesGroup(id = "proxydhcp", name = "ProxyDHCP 配置"),
    @ConfigPropertiesGroup(id = "boot", name = "启动配置")
})
public class PxeBootProperty {

    /**
     * 是否启用 PXE 服务
     */
    @ConfigProperty(value = "enable", title = "启用 PXE 服务", describe = "开启后提供 PXE/iPXE 网络启动服务", inputType = "switch", defaultValue = "false", group = "basic")
    private Boolean enable = false;

    // ==================== TFTP 配置 ====================

    /**
     * TFTP 监听地址
     */
    @ConfigProperty(value = "tftp-listen-addr", title = "TFTP 监听地址", describe = "TFTP 服务器监听的 IP 地址", defaultValue = "0.0.0.0", group = "tftp")
    private String tftpListenAddr = "0.0.0.0";

    /**
     * TFTP 监听端口
     */
    @ConfigProperty(value = "tftp-port", title = "TFTP 端口", describe = "TFTP 服务器监听的端口", defaultValue = "69", group = "tftp")
    private Integer tftpPort = 69;

    // ==================== ProxyDHCP 配置 ====================

    /**
     * 是否启用 ProxyDHCP
     */
    @ConfigProperty(value = "enable-proxydhcp", title = "启用 ProxyDHCP", describe = "当路由器不支持 PXE 参数配置时开启", inputType = "switch", defaultValue = "false", group = "proxydhcp")
    private Boolean enableProxyDhcp = false;

    /**
     * ProxyDHCP 监听地址
     */
    @ConfigProperty(value = "proxydhcp-listen-addr", title = "ProxyDHCP 监听地址", describe = "ProxyDHCP 服务监听的 IP 地址", defaultValue = "0.0.0.0", group = "proxydhcp")
    private String proxyDhcpListenAddr = "0.0.0.0";

    /**
     * TFTP 服务器地址
     */
    @ConfigProperty(value = "tftp-server-addr", title = "TFTP 服务器地址", describe = "告知客户端的 TFTP 服务器 IP，留空则自动检测", defaultValue = "", group = "proxydhcp")
    private String tftpServerAddr = "";

    // ==================== 启动配置 ====================

    /**
     * iPXE 固件路径（Legacy BIOS 版本）
     */
    @ConfigProperty(
            value = "ipxe-binary-path",
            title = "iPXE 固件路径",
            describe = "公共网盘中 iPXE 固件文件的路径（Legacy BIOS 版本）",
            defaultValue = "/pxeboot/ipxe.pxe",
            group = "boot",
            inputType = "template",
            template = "path-selector",
            templateParams = """
                    {
                        "uid": "0",
                        "label": "iPXE 固件路径",
                        "placeholder": "请选择 iPXE 固件文件",
                        "fileType": "file",
                        "editable": true,
                        "selectFile": true
                    }
                    """
    )
    private String ipxeBinaryPath = "/pxeboot/ipxe.pxe";

    /**
     * iPXE UEFI 固件路径
     */
    @ConfigProperty(
            value = "ipxe-uefi-binary-path",
            title = "iPXE UEFI 固件路径",
            describe = "公共网盘中 iPXE UEFI 固件文件的路径",
            defaultValue = "/pxeboot/ipxe-x86_64.efi",
            group = "boot",
            inputType = "template",
            template = "path-selector",
            templateParams = """
                    {
                        "uid": "0",
                        "label": "iPXE UEFI 固件路径",
                        "placeholder": "请选择 iPXE UEFI 固件文件",
                        "fileType": "file",
                        "editable": true,
                        "selectFile": true
                    }
                    """
    )
    private String ipxeUefiBinaryPath = "/pxeboot/ipxe-x86_64.efi";

    /**
     * 默认启动超时时间（秒）
     */
    @ConfigProperty(value = "default-timeout", title = "默认启动超时(秒)", describe = "启动菜单默认等待时间", defaultValue = "10", group = "boot")
    private Integer defaultTimeout = 10;
}
