package com.sfc.pxeboot.controller;

import com.sfc.pxeboot.PxeBootProperty;
import com.sfc.pxeboot.model.dto.BootItemDTO;
import com.sfc.pxeboot.model.dto.PxeServiceStatus;
import com.sfc.pxeboot.model.po.BootItem;
import com.sfc.pxeboot.server.ipxe.IpxeScriptEngine;
import com.sfc.pxeboot.server.iso.IsoHandler;
import com.sfc.pxeboot.server.proxydhcp.ProxyDhcpServer;
import com.sfc.pxeboot.server.tftp.PxeTftpServer;
import com.sfc.pxeboot.service.BootItemService;
import com.sfc.pxeboot.service.IsoResourceExtractorService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * PXE 启动管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/pxeBoot")
public class PxeBootController {

    @Autowired
    private BootItemService bootItemService;

    @Autowired
    private PxeBootProperty property;

    @Autowired
    private PxeTftpServer pxeTftpServer;

    @Autowired
    private IpxeScriptEngine ipxeScriptEngine;

    @Autowired
    private ProxyDhcpServer proxyDhcpServer;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private IsoHandler isoHandler;

    @Autowired
    private IsoResourceExtractorService isoResourceExtractorService;

    @Value("${server.port:8080}")
    private int httpPort;

    // ==================== 服务状态 ====================

    /**
     * 获取 PXE 服务状态
     */
    @GetMapping("status")
    @RolesAllowed("ADMIN")
    public JsonResult<PxeServiceStatus> status() {
        PxeServiceStatus status = PxeServiceStatus.builder()
            .tftpRunning(pxeTftpServer.isRunning())
            .httpRunning(true) // HTTP 服务始终运行（复用主应用）
            .httpPort(httpPort)
            .proxyDhcpRunning(proxyDhcpServer.isRunning())
            .tftpPort(property.getTftpPort())
            .activeBootItems(bootItemService.findEnabled().size())
            .build();
        return JsonResultImpl.getInstance(status);
    }

    // ==================== 启动项管理 ====================

    /**
     * 获取所有启动项
     */
    @GetMapping("items")
    @RolesAllowed("ADMIN")
    public JsonResult<List<BootItem>> listItems() {
        return JsonResultImpl.getInstance(bootItemService.findAll());
    }

    /**
     * 创建启动项
     */
    @PostMapping("items")
    @RolesAllowed("ADMIN")
    public JsonResult<BootItem> createItem(@RequestBody BootItemDTO dto, @AuthenticationPrincipal UserPrincipal user) {
        BootItem item = bootItemService.create(dto, user.getId());
        return JsonResultImpl.getInstance(item);
    }

    /**
     * 更新启动项
     */
    @PutMapping("items/{id}")
    @RolesAllowed("ADMIN")
    public JsonResult<BootItem> updateItem(@PathVariable Long id, @RequestBody BootItemDTO dto) {
        BootItem item = bootItemService.update(id, dto);
        UIDValidator.validateWithException(item.getUid(), true);
        return JsonResultImpl.getInstance(item);
    }

    /**
     * 删除启动项
     */
    @DeleteMapping("items/{id}")
    @RolesAllowed("ADMIN")
    public JsonResult<?> deleteItem(@PathVariable Long id) {
        bootItemService.delete(id);
        return new JsonResultImpl<>();
    }

    /**
     * 启用启动项
     */
    @PostMapping("items/{id}/enable")
    @RolesAllowed("ADMIN")
    public JsonResult<?> enableItem(@PathVariable Long id) {
        bootItemService.enable(id);
        return new JsonResultImpl<>();
    }

    /**
     * 禁用启动项
     */
    @PostMapping("items/{id}/disable")
    @RolesAllowed("ADMIN")
    public JsonResult<?> disableItem(@PathVariable Long id) {
        bootItemService.disable(id);
        return new JsonResultImpl<>();
    }

    /**
     * 更新启动项排序
     */
    @PostMapping("items/reorder")
    @RolesAllowed("ADMIN")
    public JsonResult<?> reorderItems(@RequestBody List<Long> orderedIds) {
        bootItemService.reorder(orderedIds);
        return new JsonResultImpl<>();
    }

    // ==================== 菜单预览 ====================

    /**
     * 预览 iPXE 菜单脚本
     */
    @GetMapping("menu/preview")
    @RolesAllowed("ADMIN")
    public JsonResult<String> previewMenuScript(HttpServletRequest request) {
        String script = ipxeScriptEngine.generateMenuScript(URLUtils.getBaseUrl(request.getRequestURL().toString()));
        return JsonResultImpl.getInstance(script);
    }

    // ==================== PXE 引导资源（无需认证） ====================

    @GetMapping("/boot/wimboot")
    @AllowAnonymous
    public ResponseEntity<Resource> getWimboot() throws IOException {
        String wimbootPath = property.getWimbootPath();
        if (wimbootPath == null) {
            log.warn("未配置 wimboot 路径，无法提供 wimboot 引导");
            return ResponseEntity.notFound().build();
        }
        Resource resource = diskFileSystemManager.getMainFileSystem()
                .getResource(UserConstants.PUBLIC_USER_ID, wimbootPath, null);
        if (resource == null) {
            log.warn("wimboot 文件不存在: {}", wimbootPath);
            return ResponseEntity.notFound().build();
        }
        return ResourceUtils.wrapResource(resource);
    }

    /**
     * 获取 iPXE 菜单脚本（PXE 客户端调用，无需认证）
     */
    @GetMapping("/boot/menu.ipxe")
    @AllowAnonymous
    public ResponseEntity<String> getBootMenu(
            @RequestParam(value = "server", required = false) String server,
            HttpServletRequest request
    ) {
        if (server == null || server.isEmpty()) {
            server = URLUtils.getBaseUrl(request.getRequestURL().toString());
        }
        String script = ipxeScriptEngine.generateMenuScript(server);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
            .header(HttpHeaders.CONTENT_DISPOSITION, "filename=\"menu.ipxe\"")
            .body(script);
    }

    /**
     * 获取 iPXE 固件（PXE 客户端调用，无需认证）
     */
    @GetMapping("/boot/ipxe.pxe")
    @AllowAnonymous
    public ResponseEntity<Resource> getIpxeBinary() throws IOException {
        String path = property.getIpxeBinaryPath();
        String dirPath;
        String fileName;

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            dirPath = path.substring(0, lastSlash);
            fileName = path.substring(lastSlash + 1);
        } else {
            dirPath = "/";
            fileName = path;
        }

        Resource resource = diskFileSystemManager.getMainFileSystem().getResource(0L, dirPath, fileName);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body(resource);
    }

    /**
     * 按类型从 ISO 启动项中提取引导资源（PXE 客户端调用，无需认证）
     *
     * @param itemId 启动项 ID
     * @param type   资源类型，如 kernel、initrd
     */
    @GetMapping("/boot/getIsoItem/{itemId}/type/{type}")
    @AllowAnonymous
    public ResponseEntity<Resource> getIsoItemByType(
        @PathVariable Long itemId,
        @PathVariable String type
    ) throws IOException {
        Resource resource = isoResourceExtractorService.extractByType(itemId, type);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResourceUtils.wrapResource(resource);
    }

    /**
     * 按路径从 ISO 启动项中提取引导资源（PXE 客户端调用，无需认证）
     *
     * @param itemId  启动项 ID
     * @param request HTTP 请求，用于从 URI 中提取 ISO 内文件路径
     */
    @GetMapping("/boot/getIsoItem/{itemId}/path/**")
    @AllowAnonymous
    public ResponseEntity<Resource> getIsoItemByPath(
        @PathVariable Long itemId,
        HttpServletRequest request
    ) throws IOException {
        String uri = request.getRequestURI();
        String prefix = "/api/pxeBoot/boot/getIsoItem/" + itemId + "/path";
        String path = uri.substring(uri.indexOf(prefix) + prefix.length());
        Resource resource = isoResourceExtractorService.extractByPath(itemId, path);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResourceUtils.wrapResource(resource);
    }

    /**
     * 获取启动项文件本身（PXE 客户端调用，无需认证）。
     * 仅用于 ISO 类型，返回 ISO 文件本身。
     *
     * @param itemId 启动项 ID
     */
    @GetMapping("/boot/item/{itemId}")
    @AllowAnonymous
    public ResponseEntity<Resource> getBootItemFile(@PathVariable Long itemId) throws IOException {
        BootItem item = bootItemService.findById(itemId);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }

        if (item.getType() != com.sfc.pxeboot.model.enums.BootItemType.ISO) {
            return ResponseEntity.badRequest().build();
        }

        String isoPath = item.getResourcePath();
        int lastSlash = isoPath.lastIndexOf('/');
        String isoDir = lastSlash >= 0 ? isoPath.substring(0, lastSlash) : "/";
        String isoFileName = lastSlash >= 0 ? isoPath.substring(lastSlash + 1) : isoPath;
        Resource resource = loadResource(UserConstants.PUBLIC_USER_ID, isoDir, isoFileName);

        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResourceUtils.wrapResource(resource);
    }

    /**
     * 获取启动项内指定路径的文件（PXE 客户端调用，无需认证）。
     * 对于 DIRECTORY/KERNEL_INITRD 类型，从网盘加载指定文件；
     * 对于 ISO 类型，从 ISO 内部提取指定文件。
     *
     * @param itemId  启动项 ID
     * @param request HTTP 请求，用于从 URI 中提取文件路径
     */
    @GetMapping("/boot/item/{itemId}/**")
    @AllowAnonymous
    public ResponseEntity<Resource> getBootItemFileWithFilePath(
        @PathVariable Long itemId,
        HttpServletRequest request
    ) throws IOException {
        BootItem item = bootItemService.findById(itemId);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }

        String uri = request.getRequestURI();
        String prefix = "/api/pxeBoot/boot/item/" + itemId;
        String filePath = uri.substring(uri.indexOf(prefix) + prefix.length() + 1);

        Resource resource;
        switch (item.getType()) {
            case CUSTOM_IPXE_SCRIPT:
            case KERNEL_INITRD:
                resource = loadResource(UserConstants.PUBLIC_USER_ID, item.getResourcePath(), filePath);
                break;
            case ISO:
                String isoPath = item.getResourcePath();
                int lastSlash = isoPath.lastIndexOf('/');
                String isoDir = lastSlash >= 0 ? isoPath.substring(0, lastSlash) : "/";
                String isoFileName = lastSlash >= 0 ? isoPath.substring(lastSlash + 1) : isoPath;
                Resource isoResource = loadResource(UserConstants.PUBLIC_USER_ID, isoDir, isoFileName);
                if (isoResource == null) {
                    return ResponseEntity.notFound().build();
                }
                if (filePath.startsWith("/")) {
                    resource = isoHandler.getResource(isoResource, filePath);
                } else {
                    resource = isoHandler.getResource(isoResource, "/" + filePath);
                }
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResourceUtils.wrapResource(resource);
    }

    /**
     * 从网盘加载资源
     *
     * @param uid      用户 ID
     * @param dirPath  目录路径
     * @param fileName 文件名
     * @return 文件资源，不存在时返回 null
     */
    private Resource loadResource(Long uid, String dirPath, String fileName) {
        try {
            return diskFileSystemManager.getMainFileSystem().getResource(uid, dirPath, fileName);
        } catch (Exception e) {
            log.error("加载资源失败: uid={}, {}/{}", uid, dirPath, fileName, e);
            return null;
        }
    }
}
