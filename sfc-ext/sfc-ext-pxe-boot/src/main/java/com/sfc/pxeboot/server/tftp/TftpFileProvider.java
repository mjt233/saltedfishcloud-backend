package com.sfc.pxeboot.server.tftp;

import com.sfc.pxeboot.PxeBootProperty;
import com.sfc.pxeboot.server.ipxe.IpxeScriptEngine;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * TFTP 文件提供器，负责从网盘文件系统中打开文件流。
 */
@Slf4j
@RequiredArgsConstructor
public class TftpFileProvider {
    private final static String LOG_PREFIX = "[PXE-TFTP-FILE-PROVIDER]";

    /**
     * 咸鱼云网盘文件系统
     */
    private final DiskFileSystemManager diskFileSystemManager;

    /**
     * PXE插件配置参数
     */
    private final PxeBootProperty property;

    /**
     * iPXE 启动菜单脚本引擎
     */
    private final IpxeScriptEngine ipxeScriptEngine;

    /**
     * 打开网盘文件输入流，用于流式传输。
     *
     * @param requestPath 文件路径（TFTP 客户端请求的路径）
     * @return 文件输入流，若文件不存在或打开失败返回 null
     */
    public InputStream openFileStream(String requestPath) {
        try {
            switch (requestPath) {
                case TftpConstants.ResourcePath.I_PXE -> {
                    // 响应 iPXE 固件本体（Legacy BIOS 版本）
                    String ipxeBinaryPath = property.getIpxeBinaryPath();
                    if (!StringUtils.hasText(ipxeBinaryPath)) {
                        log.warn("{} iPXE Legacy BIOS 固件路径未配置", LOG_PREFIX);
                        return null;
                    }
                    return openResourceStream(ipxeBinaryPath);
                }
                case TftpConstants.ResourcePath.I_PXE_UEFI -> {
                    // 响应 iPXE UEFI 固件
                    String ipxeUefiBinaryPath = property.getIpxeUefiBinaryPath();
                    if (!StringUtils.hasText(ipxeUefiBinaryPath)) {
                        log.warn("{} iPXE UEFI 固件路径未配置", LOG_PREFIX);
                        return null;
                    }
                    return openResourceStream(ipxeUefiBinaryPath);
                }
                case TftpConstants.ResourcePath.I_PXE_MENU -> {
                    // 响应 iPXE 固件菜单脚本
                    String scriptContent = ipxeScriptEngine.generateMenuScript();
                    return new ByteArrayInputStream(scriptContent.getBytes());
                }
                case null, default -> {
                    log.warn("{} 非预期的请求位置路径 {}", LOG_PREFIX, requestPath);
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("{} 打开文件流失败: {}", TftpConstants.LOG_PREFIX, requestPath, e);
            return null;
        }
    }

    /**
     * 从网盘文件系统中打开资源文件输入流。
     *
     * @param resourcePath 网盘中的文件路径
     * @return 文件输入流，若文件不存在或打开失败返回 null
     */
    private InputStream openResourceStream(String resourcePath) throws Exception {
        Resource resource = diskFileSystemManager.getMainFileSystem().getResource(0L, PathUtils.getParentPath(resourcePath), PathUtils.getLastNode(resourcePath));
        if (resource == null) {
            return null;
        }
        return resource.getInputStream();
    }
}
