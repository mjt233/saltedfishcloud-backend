package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 文件系统路由上下文，封装一次路径匹配后的执行语义。
 */
public class FileSystemRouteContext {
    /**
     * 匹配到的委托文件系统。主文件系统路由时为null。
     */
    private final DiskFileSystem delegateFileSystem;

    /**
     * 在对应文件系统上解析后的路径。
     */
    @Getter
    private final String resolvedPath;

    /**
     * 路由模式。
     */
    private final FileSystemRouteMode routeMode;

    /**
     * 匹配到的挂载点，若没匹配到则为null。
     */
    private final MountPoint mountPoint;

    /**
     * 构造文件系统路由上下文。
     *
     * @param delegateFileSystem 匹配到的委托文件系统，主文件系统路由时为null
     * @param mountPoint 匹配到的挂载点，主文件系统路由时为null
     * @param resolvedPath 在对应文件系统上解析后的路径
     * @param routeMode 路由模式
     */
    public FileSystemRouteContext(@Nullable DiskFileSystem delegateFileSystem,
                                  @Nullable MountPoint mountPoint,
                                  String resolvedPath,
                                  FileSystemRouteMode routeMode) {
        this.delegateFileSystem = delegateFileSystem;
        this.mountPoint = mountPoint;
        this.resolvedPath = resolvedPath;
        this.routeMode = routeMode;
    }

    /**
     * 判断当前是否命中主文件系统。
     *
     * @return 命中主文件系统返回true，否则返回false
     */
    public boolean isMainRoute() {
        return routeMode == FileSystemRouteMode.MAIN;
    }

    /**
     * 判断当前是否为挂载路由。
     *
     * @return 挂载路由返回true，否则返回false
     */
    public boolean isMountRoute() {
        return routeMode != FileSystemRouteMode.MAIN;
    }

    /**
     * 判断当前是否由主文件系统维护文件记录。
     *
     * @return 由主文件系统维护文件记录返回true，否则返回false
     */
    public boolean usesMainMetadata() {
        return routeMode == FileSystemRouteMode.MAIN || routeMode == FileSystemRouteMode.MOUNT_WITH_MAIN_METADATA;
    }

    /**
     * 判断当前挂载路由是否需要额外同步主文件系统的文件记录。
     *
     * @return 需要同步主文件系统文件记录返回true，否则返回false
     */
    public boolean requiresMainMetadataSync() {
        return isMountRoute() && usesMainMetadata();
    }

    /**
     * 判断当前是否命中挂载点根路径本身。
     *
     * @return 命中挂载点根路径返回true，否则返回false
     */
    public boolean isMountRoot() {
        return isMountRoute() && ("/".equals(resolvedPath) || resolvedPath.isEmpty());
    }

    /**
     * 获取匹配到的委托文件系统，若当前不是挂载路由则抛出异常。
     *
     * @return 委托文件系统
     */
    public DiskFileSystem requireDelegateFileSystem() {
        return Objects.requireNonNull(delegateFileSystem, "delegateFileSystem");
    }

    /**
     * 获取匹配到的挂载点，若当前未命中挂载点则抛出异常。
     *
     * @return 挂载点
     */
    public MountPoint requireMountPoint() {
        return Objects.requireNonNull(mountPoint, "mountPoint");
    }

    /**
     * 获取用于执行操作的文件系统。
     *
     * @param mainFileSystem 主文件系统
     * @return 主文件系统或委托文件系统
     */
    public DiskFileSystem getFileSystemOr(DiskFileSystem mainFileSystem) {
        return isMainRoute() ? mainFileSystem : requireDelegateFileSystem();
    }

    /**
     * 根据原始路径获取本次路由对应的实际目录路径。
     *
     * @param originPath 原始路径
     * @return 实际执行路径
     */
    public String getDelegatePath(String originPath) {
        return isMainRoute() ? originPath : resolvedPath;
    }

    /**
     * 判断当前请求路径是否命中挂载点本身。
     *
     * @param path 请求路径
     * @return 命中挂载点本身返回true，否则返回false
     */
    public boolean matchesMountPath(String path) {
        if (mountPoint == null) {
            return false;
        }
        return StringUtils.isPathEqual(path, mountPoint.getPath());
    }

    /**
     * 判断两个路由上下文是否落在同一个实际文件系统上。
     *
     * @param other 另一个路由上下文
     * @return 落在同一个实际文件系统上返回true，否则返回false
     */
    public boolean sameFileSystem(FileSystemRouteContext other) {
        if (isMainRoute() && other.isMainRoute()) {
            return true;
        }
        return isMountRoute()
                && other.isMountRoute()
                && Objects.equals(delegateFileSystem, other.delegateFileSystem);
    }

    @Override
    public String toString() {
        return "FileSystemRouteContext{" +
                "delegateFileSystem=" + delegateFileSystem +
                ", resolvedPath='" + resolvedPath + '\'' +
                ", routeMode=" + routeMode +
                '}';
    }
}
