package com.sfc.pxeboot.server.ipxe;

import com.sfc.pxeboot.PxeBootProperty;
import com.sfc.pxeboot.model.enums.IsoBootMethod;
import com.sfc.pxeboot.model.po.BootItem;
import com.sfc.pxeboot.service.BootMenuManager;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

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
     * 生成 iPXE 菜单脚本。
     *
     * @param baseUrl 服务器 HTTP 基础 URL（协议 + 主机名/IP + 端口），末尾不带 /
     * @return iPXE 脚本内容
     */
    public String generateMenuScript(String baseUrl) {
        List<BootItem> items = bootMenuManager.getActiveItems();
        int timeout = property.getDefaultTimeout() * 1000;

        String menuItems = items.stream()
                .map(item -> {
                    String desc = StringUtils.hasText(item.getDescription()) ? item.getDescription() : item.getDisplayName();
                    return "item %s %s".formatted(item.getItemKey(), desc);
                })
                .collect(Collectors.joining("\n"));

        String bootScripts = items.stream()
                .map(item -> ":%s\n%s".formatted(item.getItemKey(), generateItemBootScript(item, baseUrl)))
                .collect(Collectors.joining("\n"));

        return """
                #!ipxe

                set timeout %d

                menu Salted Fish Cloud PXE Boot
                item --gap -- Please select boot option:
                %s
                item shell iPXE Shell

                choose --timeout ${timeout} target && goto ${target}

                %s

                :shell
                shell
                """.formatted(timeout, menuItems, bootScripts);
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
        String kernelUrl = baseUrl + "/item?itemId=" + item.getId() + "&filePath=" + item.getKernelFilename();
        String initrdUrl = baseUrl + "/item?itemId=" + item.getId() + "&filePath=" + item.getInitrdFilename();

        String initrdParam = item.getInitrdFilename() != null
                ? " initrd=" + item.getInitrdFilename() : "";
        String kernelParams = item.getKernelParams() != null && !item.getKernelParams().isEmpty()
                ? " " + item.getKernelParams() : "";
        String initrdLine = item.getInitrdFilename() != null
                ? "initrd %s\n".formatted(initrdUrl) : "";

        return """
                kernel %s%s%s
                %sboot
                """.formatted(kernelUrl, initrdParam, kernelParams, initrdLine);
    }

    /**
     * 生成目录引导脚本
     */
    private String generateDirectoryBoot(BootItem item, String baseUrl) {
        String itemBaseUrl = baseUrl + "/item?itemId=" + item.getId();
        String kernelParams = item.getKernelParams() != null && !item.getKernelParams().isEmpty()
                ? " " + item.getKernelParams() : "";

        return """
                kernel %s&filePath=vmlinuz%s
                initrd %s&filePath=initrd.img
                boot
                """.formatted(itemBaseUrl, kernelParams, itemBaseUrl);
    }

    /**
     * 生成 ISO 引导脚本
     */
    private String generateIsoBoot(BootItem item, String baseUrl) {
        String itemBaseUrl = baseUrl + "/item?itemId=" + item.getId();
        var bootMethod = item.getIsoBootMethod();

        if (bootMethod == IsoBootMethod.WIMBOOT) {
            return """
                    kernel wimboot
                    initrd %s&filePath=bootmgr bootmgr
                    initrd %s&filePath=Boot/BCD BCD
                    initrd %s&filePath=Boot/boot.sdi boot.sdi
                    initrd %s&filePath=sources/boot.wim boot.wim
                    boot
                    """.formatted(itemBaseUrl, itemBaseUrl, itemBaseUrl, itemBaseUrl);
        }

        if (bootMethod == IsoBootMethod.SANBOOT) {
            return "sanboot %s\n".formatted(itemBaseUrl);
        }

        if (bootMethod == IsoBootMethod.KERNEL) {
            String kernelParams = item.getKernelParams() != null
                    ? " " + item.getKernelParams() : "";
            return """
                    kernel %s&filePath=vmlinuz%s
                    initrd %s&filePath=initrd.img
                    boot
                    """.formatted(itemBaseUrl, kernelParams, itemBaseUrl);
        }

        // MEMDISK 及默认方式：filePath 为空，获取 ISO 文件本身
        return """
                kernel memdisk
                initrd %s
                boot
                """.formatted(itemBaseUrl);
    }
}
