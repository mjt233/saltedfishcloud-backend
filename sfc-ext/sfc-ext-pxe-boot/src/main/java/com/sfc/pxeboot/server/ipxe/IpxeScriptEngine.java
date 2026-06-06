package com.sfc.pxeboot.server.ipxe;

import com.sfc.pxeboot.PxeBootProperty;
import com.sfc.pxeboot.model.enums.IsoBootMethod;
import com.sfc.pxeboot.model.po.BootItem;
import com.sfc.pxeboot.service.BootMenuManager;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
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
        String bootApiUrl = baseUrl + "/api/pxeBoot/boot";

        String menuItems = items.stream()
                .map(item -> {
                    String desc = StringUtils.hasText(item.getDescription()) ? item.getDescription() : item.getDisplayName();
                    return "item %s %s".formatted(item.getItemKey(), desc);
                })
                .collect(Collectors.joining("\n"));

        String bootScripts = items.stream()
                .map(item -> ":%s\n%s".formatted(item.getItemKey(), generateItemBootScript(item)))
                .collect(Collectors.joining("\n"));

        return ScriptTemplate.render("""
                #!ipxe

                set base_url {{base_url}}
                set timeout {{timeout}}

                menu Salted Fish Cloud PXE Boot
                item --gap -- Please select boot option:
                {{menu_items}}
                item shell iPXE Shell

                choose --timeout ${timeout} target && goto ${target}

                {{boot_scripts}}

                :shell
                shell
                """, Map.of(
                "base_url", bootApiUrl,
                "timeout", String.valueOf(timeout),
                "menu_items", menuItems,
                "boot_scripts", bootScripts
        ));
    }

    /**
     * 生成单个启动项的引导脚本
     */
    private String generateItemBootScript(BootItem item) {
        return switch (item.getType()) {
            case KERNEL_INITRD -> generateKernelBoot(item);
            case DIRECTORY -> generateDirectoryBoot(item);
            case ISO -> generateIsoBoot(item);
        };
    }

    /**
     * 生成 Linux 内核 + initrd 引导脚本
     */
    private String generateKernelBoot(BootItem item) {
        String kernelUrl = "${base_url}/item?itemId=" + item.getId() + "&filePath=" + item.getKernelFilename();
        String initrdUrl = "${base_url}/item?itemId=" + item.getId() + "&filePath=" + item.getInitrdFilename();

        String initrdParam = item.getInitrdFilename() != null
                ? " initrd=" + item.getInitrdFilename() : "";
        String kernelParams = item.getKernelParams() != null && !item.getKernelParams().isEmpty()
                ? " " + item.getKernelParams() : "";
        String initrdLine = item.getInitrdFilename() != null
                ? "initrd %s\n".formatted(initrdUrl) : "";

        return ScriptTemplate.render("""
                kernel {{kernel_url}}{{initrd_param}}{{kernel_params}}
                {{initrd_line}}boot
                """, Map.of(
                "kernel_url", kernelUrl,
                "initrd_param", initrdParam,
                "kernel_params", kernelParams,
                "initrd_line", initrdLine
        ));
    }

    /**
     * 生成目录引导脚本
     */
    private String generateDirectoryBoot(BootItem item) {
        String itemBaseUrl = "${base_url}/item?itemId=" + item.getId();
        String kernelParams = item.getKernelParams() != null && !item.getKernelParams().isEmpty()
                ? " " + item.getKernelParams() : "";

        return ScriptTemplate.render("""
                kernel {{item_base_url}}&filePath=vmlinuz{{kernel_params}}
                initrd {{item_base_url}}&filePath=initrd.img
                boot
                """, Map.of(
                "item_base_url", itemBaseUrl,
                "kernel_params", kernelParams
        ));
    }

    /**
     * 生成 ISO 引导脚本
     */
    private String generateIsoBoot(BootItem item) {
        String itemBaseUrl = "${base_url}/item?itemId=" + item.getId();
        var bootMethod = item.getIsoBootMethod();

        if (bootMethod == IsoBootMethod.WIMBOOT) {
            return ScriptTemplate.render("""
                    kernel wimboot
                    initrd {{item_base_url}}&filePath=bootmgr bootmgr
                    initrd {{item_base_url}}&filePath=Boot/BCD BCD
                    initrd {{item_base_url}}&filePath=Boot/boot.sdi boot.sdi
                    initrd {{item_base_url}}&filePath=sources/boot.wim boot.wim
                    boot
                    """, Map.of("item_base_url", itemBaseUrl));
        }

        if (bootMethod == IsoBootMethod.SANBOOT) {
            return "sanboot %s\n".formatted(itemBaseUrl);
        }

        if (bootMethod == IsoBootMethod.KERNEL) {
            String kernelParams = item.getKernelParams() != null
                    ? " " + item.getKernelParams() : "";
            return ScriptTemplate.render("""
                    kernel {{item_base_url}}&filePath=vmlinuz{{kernel_params}}
                    initrd {{item_base_url}}&filePath=initrd.img
                    boot
                    """, Map.of(
                    "item_base_url", itemBaseUrl,
                    "kernel_params", kernelParams
            ));
        }

        // MEMDISK 及默认方式：filePath 为空，获取 ISO 文件本身
        return """
                kernel memdisk
                initrd %s
                boot
                """.formatted(itemBaseUrl);
    }
}
