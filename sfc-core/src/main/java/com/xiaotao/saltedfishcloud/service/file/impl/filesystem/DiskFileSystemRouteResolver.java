package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.StorageRegistry;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 文件系统路由解析器，只负责根据路径匹配挂载点与实际执行文件系统。
 */
@Component
public class DiskFileSystemRouteResolver {
    /**
     * 挂载点服务。
     */
    @Autowired
    @Lazy
    private MountPointService mountPointService;

    /**
     * 存储注册表。
     */
    @Autowired
    @Lazy
    private StorageRegistry storageRegistry;

    /**
     * 挂载存储适配为文件系统时使用的缩略图服务。
     */
    @Autowired(required = false)
    private ThumbnailService thumbnailService;

    /**
     * 挂载存储到文件系统适配器缓存。
     */
    private final Map<Storage, DiskFileSystem> storageAdapterCache = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 根据请求路径匹配对应的文件系统。
     *
     * @param uid 用户ID
     * @param path 请求路径
     * @return 文件系统匹配结果
     */
    public FileSystemRouteContext matchFileSystem(long uid, String path) {
        MountPoint mountPoint = matchMountPoint(uid, path);
        if (mountPoint == null) {
            return new FileSystemRouteContext(null, null, path, FileSystemRouteMode.MAIN);
        }
        try {
            Map<String, Object> map = MapperHolder.parseJsonToMap(mountPoint.getParams());
            Storage storage = storageRegistry.getStorage(mountPoint.getProtocol(), map);
            DiskFileSystem fileSystem = getStorageAdapter(storage);
            FileSystemRouteMode routeMode = Boolean.TRUE.equals(mountPoint.getIsProxyStoreRecord())
                    ? FileSystemRouteMode.MOUNT_WITH_MAIN_METADATA
                    : FileSystemRouteMode.MOUNT_WITH_DELEGATED_METADATA;
            return new FileSystemRouteContext(fileSystem, mountPoint, resolvePath(mountPoint.getPath(), path), routeMode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据给定的用户与请求路径匹配所在挂载点。
     *
     * @param uid 用户ID
     * @param path 请求路径
     * @return 匹配到的挂载点，未匹配到时返回null
     */
    private MountPoint matchMountPoint(long uid, String path) {
        Map<String, MountPoint> mountPointMap = mountPointService.findMountPointPathByUid(uid);
        String name = StringUtils.getURLLastName(path);
        if (name == null) {
            return null;
        }
        String pathWithSeparator = path + "/";
        return mountPointMap.entrySet().stream()
                .filter(e -> pathWithSeparator.startsWith(e.getKey()))
                .findAny()
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    /**
     * 获取挂载存储对应的文件系统适配器。
     *
     * @param storage 挂载存储
     * @return 文件系统适配器
     */
    private DiskFileSystem getStorageAdapter(Storage storage) {
        synchronized (storageAdapterCache) {
            return storageAdapterCache.computeIfAbsent(storage, key -> {
                StorageDiskFileSystemAdapter adapter = new StorageDiskFileSystemAdapter(key);
                adapter.setThumbnailService(thumbnailService);
                return adapter;
            });
        }
    }

    /**
     * 将请求路径解析为挂载点内部的相对路径。
     *
     * @param mountPath 挂载点路径
     * @param requestPath 请求路径
     * @return 挂载点内部相对路径
     */
    private String resolvePath(String mountPath, String requestPath) {
        int prefixLength = mountPath.endsWith("/") ? mountPath.length() - 1 : mountPath.length();
        if (requestPath.startsWith("/")) {
            return requestPath.substring(prefixLength);
        }
        return StringUtils.appendPath("/", requestPath.substring(prefixLength));
    }
}
