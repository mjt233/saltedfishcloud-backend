package com.sfc.pxeboot.controller;

import com.sfc.pxeboot.PxeBootProperty;
import com.sfc.pxeboot.model.dto.BootItemDTO;
import com.sfc.pxeboot.model.dto.PxeServiceStatus;
import com.sfc.pxeboot.model.dto.PxeSessionInfo;
import com.sfc.pxeboot.model.po.BootItem;
import com.sfc.pxeboot.server.ipxe.IpxeScriptEngine;
import com.sfc.pxeboot.server.iso.IsoHandler;
import com.sfc.pxeboot.server.tftp.PxeTftpServer;
import com.sfc.pxeboot.service.BootItemService;
import com.sfc.pxeboot.service.BootMenuManager;
import com.sfc.pxeboot.service.PxeSessionTracker;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import jakarta.annotation.security.RolesAllowed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * PXE 启动管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/pxeBoot")
@RolesAllowed("ADMIN")
public class PxeBootController {

    @Autowired
    private BootItemService bootItemService;

    @Autowired
    private BootMenuManager bootMenuManager;

    @Autowired
    private PxeSessionTracker sessionTracker;

    @Autowired
    private PxeBootProperty property;

    @Autowired
    private PxeTftpServer pxeTftpServer;

    @Autowired
    private IpxeScriptEngine ipxeScriptEngine;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private IsoHandler isoHandler;

    // ==================== 服务状态 ====================

    /**
     * 获取 PXE 服务状态
     */
    @GetMapping("status")
    public JsonResult<PxeServiceStatus> status() {
        PxeServiceStatus status = PxeServiceStatus.builder()
            .tftpRunning(pxeTftpServer.isRunning())
            .httpRunning(true) // HTTP 服务始终运行（复用主应用）
            .proxyDhcpRunning(false) // TODO: 添加 ProxyDHCP 状态
            .tftpPort(property.getTftpPort())
            .httpPort(0) // 使用主应用端口
            .activeBootItems(bootMenuManager.getActiveItems().size())
            .activeSessions(sessionTracker.getActiveSessions().size())
            .build();
        return JsonResultImpl.getInstance(status);
    }

    // ==================== 启动项管理 ====================

    /**
     * 获取所有启动项
     */
    @GetMapping("items")
    public JsonResult<List<BootItem>> listItems() {
        return JsonResultImpl.getInstance(bootItemService.findAll());
    }

    /**
     * 创建启动项
     */
    @PostMapping("items")
    public JsonResult<BootItem> createItem(@RequestBody BootItemDTO dto, @UID Long uid) {
        BootItem item = bootItemService.create(dto, uid);
        bootMenuManager.refresh();
        return JsonResultImpl.getInstance(item);
    }

    /**
     * 更新启动项
     */
    @PutMapping("items/{id}")
    public JsonResult<BootItem> updateItem(@PathVariable Long id, @RequestBody BootItemDTO dto) {
        BootItem item = bootItemService.update(id, dto);
        bootMenuManager.refresh();
        return JsonResultImpl.getInstance(item);
    }

    /**
     * 删除启动项
     */
    @DeleteMapping("items/{id}")
    public JsonResult<?> deleteItem(@PathVariable Long id) {
        bootItemService.delete(id);
        bootMenuManager.refresh();
        return new JsonResultImpl<>();
    }

    /**
     * 启用启动项
     */
    @PostMapping("items/{id}/enable")
    public JsonResult<?> enableItem(@PathVariable Long id) {
        bootItemService.enable(id);
        bootMenuManager.refresh();
        return new JsonResultImpl<>();
    }

    /**
     * 禁用启动项
     */
    @PostMapping("items/{id}/disable")
    public JsonResult<?> disableItem(@PathVariable Long id) {
        bootItemService.disable(id);
        bootMenuManager.refresh();
        return new JsonResultImpl<>();
    }

    /**
     * 更新启动项排序
     */
    @PostMapping("items/reorder")
    public JsonResult<?> reorderItems(@RequestBody List<Long> orderedIds) {
        bootItemService.reorder(orderedIds);
        bootMenuManager.refresh();
        return new JsonResultImpl<>();
    }

    // ==================== 会话监控 ====================

    /**
     * 获取活跃的 PXE 会话
     */
    @GetMapping("sessions")
    public JsonResult<List<PxeSessionInfo>> activeSessions() {
        return JsonResultImpl.getInstance(sessionTracker.getActiveSessions());
    }

    // ==================== 菜单预览 ====================

    /**
     * 预览 iPXE 菜单脚本
     */
    @GetMapping("menu/preview")
    public JsonResult<String> previewMenuScript() {
        String script = ipxeScriptEngine.generateMenuScript("localhost");
        return JsonResultImpl.getInstance(script);
    }

    // ==================== PXE 引导资源（无需认证） ====================

    /**
     * 获取 iPXE 菜单脚本（PXE 客户端调用，无需认证）
     */
    @GetMapping("/boot/menu.ipxe")
    @AllowAnonymous
    public ResponseEntity<String> getBootMenu(@RequestParam(value = "server", required = false) String server) {
        if (server == null || server.isEmpty()) {
            server = "localhost";
        }
        String script = ipxeScriptEngine.generateMenuScript(server);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"menu.ipxe\"")
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
     * 获取启动项文件（PXE 客户端调用，无需认证）
     */
    @GetMapping("/boot/item/{itemId}/**")
    @AllowAnonymous
    public ResponseEntity<Resource> getBootItemFile(
        @PathVariable Long itemId,
        @RequestParam(value = "file", required = false) String filePath) throws IOException {

        BootItem item = bootItemService.findById(itemId);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource;
        switch (item.getType()) {
            case DIRECTORY:
            case KERNEL_INITRD:
                resource = loadResource(item.getUid(), item.getResourcePath(), filePath);
                break;
            case ISO:
                String isoPath = item.getResourcePath();
                int lastSlash = isoPath.lastIndexOf('/');
                String isoDir = lastSlash >= 0 ? isoPath.substring(0, lastSlash) : "/";
                String isoFileName = lastSlash >= 0 ? isoPath.substring(lastSlash + 1) : isoPath;
                resource = isoHandler.getFileStream(item.getUid(), isoDir, isoFileName, filePath);
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
            .body(resource);
    }

    /**
     * 从网盘加载资源
     */
    private Resource loadResource(Long uid, String dirPath, String fileName) {
        try {
            return diskFileSystemManager.getMainFileSystem().getResource(uid, dirPath, fileName);
        } catch (Exception e) {
            log.error("加载资源失败: {}/{}", dirPath, fileName, e);
            return null;
        }
    }
}
