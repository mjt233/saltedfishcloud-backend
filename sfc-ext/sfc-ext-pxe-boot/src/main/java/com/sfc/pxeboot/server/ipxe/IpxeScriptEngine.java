package com.sfc.pxeboot.server.ipxe;

import com.sfc.pxeboot.PxeBootProperty;
import com.sfc.pxeboot.model.po.BootItem;
import com.sfc.pxeboot.service.BootMenuManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * iPXE 脚本引擎
 * 动态生成 iPXE 启动菜单脚本
 */
@Slf4j
public class IpxeScriptEngine {

    @Autowired
    private BootMenuManager bootMenuManager;

    @Autowired
    private PxeBootProperty property;

    /**
     * 生成 iPXE 菜单脚本
     *
     * @param serverAddress http服务器地址
     * @return iPXE 脚本内容
     */
    public String generateMenuScript(String serverAddress) {
        List<BootItem> items = bootMenuManager.getActiveItems();
        int timeout = property.getDefaultTimeout() * 1000;

        StringBuilder sb = new StringBuilder();
        sb.append("#!ipxe\n\n");
        sb.append("set timeout ").append(timeout).append("\n\n");
        sb.append("menu SFC PXE Boot Menu\n");
        sb.append("item --gap -- 请选择启动项:\n");

        // 添加菜单项
        for (BootItem item : items) {
            String desc = item.getDescription() != null ? item.getDescription() : item.getDisplayName();
            sb.append("item ").append(item.getItemKey()).append(" ").append(desc).append("\n");
        }

        sb.append("item shell iPXE Shell\n\n");
        sb.append("choose --timeout ${timeout} target && goto ${target}\n\n");

        // 生成各启动项的脚本
        for (BootItem item : items) {
            sb.append(":").append(item.getItemKey()).append("\n");
            sb.append(generateItemBootScript(item, serverAddress));
            sb.append("\n");
        }

        // iPXE Shell
        sb.append(":shell\n");
        sb.append("shell\n");

        return sb.toString();
    }

    /**
     * 生成单个启动项的引导脚本
     */
    private String generateItemBootScript(BootItem item, String serverAddress) {
        // 使用主应用的 HTTP 端口（通过 ${next-server} 变量由 iPXE 自动获取）
        String baseUrl = serverAddress + "/api/pxeBoot/boot";

        return switch (item.getType()) {
            case KERNEL_INITRD -> generateKernelBoot(item, baseUrl);
            case DIRECTORY -> generateDirectoryBoot(item, baseUrl);
            case ISO -> generateIsoBoot(item, baseUrl);
        };
    }

    /**
     * 生成 Linux 内核 + initrd 引导脚本
     */
    private String generateKernelBoot(BootItem item, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        String itemBaseUrl = baseUrl + "/item/" + item.getId();

        String kernelUrl = itemBaseUrl + "?file=" + item.getKernelFilename();
        String initrdUrl = itemBaseUrl + "?file=" + item.getInitrdFilename();

        sb.append("kernel ").append(kernelUrl);
        if (item.getInitrdFilename() != null) {
            sb.append(" initrd=").append(item.getInitrdFilename());
        }
        if (item.getKernelParams() != null && !item.getKernelParams().isEmpty()) {
            sb.append(" ").append(item.getKernelParams());
        }
        sb.append("\n");

        if (item.getInitrdFilename() != null) {
            sb.append("initrd ").append(initrdUrl).append("\n");
        }

        sb.append("boot\n");
        return sb.toString();
    }

    /**
     * 生成目录引导脚本
     */
    private String generateDirectoryBoot(BootItem item, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        String itemBaseUrl = baseUrl + "/item/" + item.getId();

        sb.append("kernel ").append(itemBaseUrl).append("?file=vmlinuz");
        if (item.getKernelParams() != null && !item.getKernelParams().isEmpty()) {
            sb.append(" ").append(item.getKernelParams());
        }
        sb.append("\n");
        sb.append("initrd ").append(itemBaseUrl).append("?file=initrd.img\n");
        sb.append("boot\n");

        return sb.toString();
    }

    /**
     * 生成 ISO 引导脚本
     */
    private String generateIsoBoot(BootItem item, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        String itemBaseUrl = baseUrl + "/item/" + item.getId();

        if (item.getIsoBootMethod() != null) {
            switch (item.getIsoBootMethod()) {
                case KERNEL:
                    sb.append("kernel ").append(itemBaseUrl).append("?file=vmlinuz");
                    if (item.getKernelParams() != null) {
                        sb.append(" ").append(item.getKernelParams());
                    }
                    sb.append("\n");
                    sb.append("initrd ").append(itemBaseUrl).append("?file=initrd.img\n");
                    sb.append("boot\n");
                    break;

                case WIMBOOT:
                    sb.append("kernel wimboot\n");
                    sb.append("initrd ").append(itemBaseUrl).append("?file=bootmgr bootmgr\n");
                    sb.append("initrd ").append(itemBaseUrl).append("?file=Boot/BCD BCD\n");
                    sb.append("initrd ").append(itemBaseUrl).append("?file=Boot/boot.sdi boot.sdi\n");
                    sb.append("initrd ").append(itemBaseUrl).append("?file=sources/boot.wim boot.wim\n");
                    sb.append("boot\n");
                    break;

                case MEMDISK:
                    sb.append("kernel memdisk\n");
                    sb.append("initrd ").append(itemBaseUrl).append("?file=image.iso\n");
                    sb.append("boot\n");
                    break;

                case SANBOOT:
                    sb.append("sanboot ").append(itemBaseUrl).append("?file=image.iso\n");
                    break;

                default:
                    sb.append("kernel memdisk\n");
                    sb.append("initrd ").append(itemBaseUrl).append("?file=image.iso\n");
                    sb.append("boot\n");
                    break;
            }
        } else {
            sb.append("kernel memdisk\n");
            sb.append("initrd ").append(itemBaseUrl).append("?file=image.iso\n");
            sb.append("boot\n");
        }

        return sb.toString();
    }
}
