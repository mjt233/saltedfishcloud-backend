package com.sfc.pxeboot.server.ipxe;

import com.sfc.pxeboot.PxeBootProperty;
import com.sfc.pxeboot.model.enums.BootItemType;
import com.sfc.pxeboot.model.enums.IsoBootMethod;
import com.sfc.pxeboot.model.po.BootItem;
import com.sfc.pxeboot.service.BootItemService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * iPXE 脚本引擎
 * 动态生成 iPXE 启动菜单脚本
 */
@Slf4j
public class IpxeScriptEngine {

    @Autowired
    private BootItemService bootItemService;

    @Autowired
    private PxeBootProperty property;

    /**
     * 生成 iPXE 菜单脚本。
     *
     * @param baseUrl 服务器 HTTP 基础 URL（协议 + 主机名/IP + 端口），末尾不带 /
     * @return iPXE 脚本内容
     */
    public String generateMenuScript(String baseUrl) {
        List<BootItem> items = bootItemService.findEnabled();
        int timeout = property.getDefaultTimeout() * 1000;

        String menuItems = items.stream()
                .map(item -> {
                    String desc = StringUtils.hasText(item.getDescription()) ? item.getDescription() : item.getDisplayName();
                    return "item %s %s".formatted(item.getItemKey(), desc);
                })
                .collect(Collectors.joining("\n"));

        String bootScripts = items.stream()
                .map(item -> ScriptTemplate.render("""
                                :{{label}}
                                set res_url {{res_url}}
                                {{script_content}}
                                {{boot_action}}
                                """,
                        Map.of(
                                "label", item.getItemKey(),
                                "script_content", generateItemBootScript(item),
                                "res_url", "${base_url}/api/pxeBoot/boot/item/" + item.getId(),
                                "boot_action", item.getType() == BootItemType.CUSTOM_IPXE_SCRIPT || (item.getType() == BootItemType.ISO && item.getIsoBootMethod() == IsoBootMethod.SANBOOT) ? "" : "boot"
                        )))
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
                "base_url", baseUrl,
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
            case CUSTOM_IPXE_SCRIPT -> Optional.ofNullable(item.getCustomIpxeScript()).orElse("");
            case ISO -> generateIsoBoot(item);
        };
    }

    /**
     * 生成 Linux 内核 + initrd 引导脚本
     */
    private String generateKernelBoot(BootItem item) {
        String kernelUrl = "${res_url}/" + item.getKernelFilename();
        String initrdUrl = "${res_url}/" + item.getInitrdFilename();

        String initrdParam = item.getInitrdFilename() != null
                ? " initrd=" + item.getInitrdFilename() : "";
        String kernelParams = item.getKernelParams() != null && !item.getKernelParams().isEmpty()
                ? " " + item.getKernelParams() : "";
        String initrdLine = item.getInitrdFilename() != null
                ? "initrd %s\n".formatted(initrdUrl) : "";

        return ScriptTemplate.render("""
                kernel {{kernel_url}}{{initrd_param}}{{kernel_params}}
                {{initrd_line}}
                """, Map.of(
                "kernel_url", kernelUrl,
                "initrd_param", initrdParam,
                "kernel_params", kernelParams,
                "initrd_line", initrdLine
        ));
    }

    /**
     * 生成 ISO 引导脚本
     */
    private String generateIsoBoot(BootItem item) {
        var bootMethod = item.getIsoBootMethod();

        if (bootMethod == IsoBootMethod.WIMBOOT) {
            return """
                    kernel wimboot
                    initrd ${res_url}/bootmgr bootmgr
                    initrd ${res_url}/Boot/BCD BCD
                    initrd ${res_url}/Boot/boot.sdi boot.sdi
                    initrd ${res_url}/sources/boot.wim boot.wim
                    """;
        }

        if (bootMethod == IsoBootMethod.SANBOOT) {
            return "sanboot ${res_url}";
        }

        if (bootMethod == IsoBootMethod.KERNEL) {
            String kernelParams = item.getKernelParams() != null
                    ? " " + item.getKernelParams() : "";
            String kernelUrl = "${base_url}/api/pxeBoot/boot/getIsoItem/" + item.getId() + "/type/kernel";
            String initrdUrl = "${base_url}/api/pxeBoot/boot/getIsoItem/" + item.getId() + "/type/initrd";
            return ScriptTemplate.render("""
                    set iso_url {{iso_url}}
                    kernel --name kernel {{kernel_url}}{{kernel_params}}
                    initrd --name initrd.img {{initrd_url}}
                    """, Map.of(
                    "kernel_url", kernelUrl,
                    "initrd_url", initrdUrl,
                    "kernel_params", kernelParams
            ));
        }

        // MEMDISK 及默认方式：filePath 为空，获取 ISO 文件本身
        return """
                kernel memdisk
                initrd ${res_url}
                """;
    }
}
