package com.sfc.pxeboot.service;

import com.sfc.pxeboot.model.po.BootItem;
import com.sfc.pxeboot.server.iso.IsoFileEntry;
import com.sfc.pxeboot.server.iso.IsoHandler;
import com.xiaotao.saltedfishcloud.cache.LockFactory;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorage;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageDomainDefinition;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * ISO 启动项资源提取服务。
 * <p>支持按路径和按类型两种提取方式，提取结果通过 AttachStorage 缓存，
 * 并使用 LockFactory 防止并发提取。</p>
 */
@Slf4j
@Service
public class IsoResourceExtractorService {

    private static final String LOG_PREFIX = "[PXE-ISO-EXTRACT]";
    private static final String STORAGE_DOMAIN_ID = "pxe-boot";
    private static final String LOCK_PREFIX = "pxe-boot:extract:";

    // Phase 1: 已知精确路径
    private static final Map<String, List<String>> KNOWN_PATHS = Map.of(
        "kernel", List.of("/casper/vmlinuz", "/images/pxeboot/vmlinuz", "/boot/vmlinuz"),
        "initrd", List.of("/casper/initrd", "/casper/initrd.gz", "/images/pxeboot/initrd.img", "/boot/initrd.img")
    );

    // Phase 2: 已知目录正则模式（Proxmox VE）
    private static final Map<String, String[]> KNOWN_DIR_PATTERNS = Map.of(
        "kernel", new String[]{"/boot", "^linux\\d+$"},
        "initrd", new String[]{"/boot", "^initrd\\.img$"}
    );

    // Phase 3: 全局正则扫描兜底
    private static final Map<String, String> FALLBACK_PATTERNS = Map.of(
        "kernel", "^vmlinuz",
        "initrd", "^initrd|^initramfs"
    );

    private AttachStorage cacheStorage;

    @Autowired
    private BootItemService bootItemService;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private IsoHandler isoHandler;

    @Autowired
    private LockFactory lockFactory;

    /**
     * 注册 PXE 启动缓存存储域。
     *
     * @param attachStorageManager 附属存储管理器
     */
    @Autowired
    public void setAttachStorageManager(AttachStorageManager attachStorageManager) {
        attachStorageManager.registerStorageDomain(
            AttachStorageDomainDefinition.builder()
                .id(STORAGE_DOMAIN_ID)
                .name("PXE启动缓存")
                .description("PXE启动项ISO资源提取缓存")
                .build()
        );
        this.cacheStorage = attachStorageManager.getStorage(STORAGE_DOMAIN_ID);
    }

    /**
     * 从 ISO 启动项中按指定路径提取资源（带缓存）。
     * <p>使用双重检查锁模式防止并发提取。</p>
     *
     * @param itemId 启动项 ID
     * @param path   ISO 内文件路径
     * @return 提取后的文件资源
     * @throws IOException 如果提取失败
     */
    public Resource extractByPath(Long itemId, String path) throws IOException {
        String cachePath = buildCachePath(itemId, "path_" + sha256Hex(path));
        String lockKey = LOCK_PREFIX + cachePath;
        String fileName = Path.of(path).getFileName().toString();

        // 第一次检查缓存
        if (cacheStorage.exist(cachePath)) {
            log.debug("{} 缓存命中: {}", LOG_PREFIX, cachePath);
            return cacheStorage.getFile(cachePath)
                .map(r -> (Resource) new NamedResource(r, fileName))
                .orElse(null);
        }

        Lock lock = lockFactory.getLock(lockKey);
        lock.lock();
        try {
            // 第二次检查缓存
            if (cacheStorage.exist(cachePath)) {
                log.debug("{} 锁内缓存命中: {}", LOG_PREFIX, cachePath);
                return cacheStorage.getFile(cachePath)
                    .map(r -> (Resource) new NamedResource(r, fileName))
                    .orElse(null);
            }

            // 从 ISO 提取
            BootItem item = bootItemService.findById(itemId);
            Resource isoResource = loadIsoResource(item);
            Resource resource = isoHandler.getResource(isoResource, path);
            if (resource == null) {
                return null;
            }

            // 存入缓存
            cacheStorage.saveFile(cachePath, resource);

            log.info("{} 已提取并缓存: itemId={}, path={}", LOG_PREFIX, itemId, path);
            return cacheStorage.getFile(cachePath)
                .map(r -> (Resource) new NamedResource(r, fileName))
                .orElse(null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从 ISO 启动项中按资源类型自动提取 kernel 或 initrd（带缓存）。
     * <p>采用三阶段探测策略：已知精确路径 → 已知目录正则扫描 → 全局正则扫描兜底。</p>
     *
     * @param itemId 启动项 ID
     * @param type   资源类型，可选 "kernel"、"initrd"
     * @return 提取后的文件资源。资源不存在则返回null。
     * @throws IOException 如果提取失败
     */
    public Resource extractByType(Long itemId, String type) throws IOException {
        if (!KNOWN_PATHS.containsKey(type)) {
            throw new IllegalArgumentException("不支持的资源类型: " + type + "，可选值: kernel, initrd");
        }

        String cachePath = buildCachePath(itemId, "type_" + type);
        String lockKey = LOCK_PREFIX + cachePath;

        // 第一次检查缓存
        if (cacheStorage.exist(cachePath)) {
            log.debug("{} 缓存命中: {}", LOG_PREFIX, cachePath);
            return getCachedResource(cachePath);
        }

        Lock lock = lockFactory.getLock(lockKey);
        lock.lock();
        try {
            // 第二次检查缓存
            if (cacheStorage.exist(cachePath)) {
                log.debug("{} 锁内缓存命中: {}", LOG_PREFIX, cachePath);
                return getCachedResource(cachePath);
            }

            BootItem item = bootItemService.findById(itemId);
            Resource isoResource = loadIsoResource(item);

            // Phase 1: 已知精确路径探测
            Resource fileResource = tryKnownPaths(isoResource, KNOWN_PATHS.get(type));

            // Phase 2: 已知目录正则扫描
            if (fileResource == null) {
                String[] dirPattern = KNOWN_DIR_PATTERNS.get(type);
                if (dirPattern != null) {
                    List<IsoFileEntry> matches = isoHandler.findFilesByPattern(isoResource, dirPattern[1], dirPattern[0]);
                    if (!matches.isEmpty()) {
                        log.debug("{} Phase 2 命中: {}", LOG_PREFIX, matches.getFirst().getPath());
                        fileResource = isoHandler.getResource(isoResource, matches.getFirst().getPath());
                    }
                }
            }

            // Phase 3: 全局正则扫描兜底
            if (fileResource == null) {
                String fallbackPattern = FALLBACK_PATTERNS.get(type);
                List<IsoFileEntry> matches = isoHandler.findFilesByPattern(isoResource, fallbackPattern, null);
                if (!matches.isEmpty()) {
                    log.debug("{} Phase 3 命中: {}", LOG_PREFIX, matches.getFirst().getPath());
                    fileResource = isoHandler.getResource(isoResource, matches.getFirst().getPath());
                }
            }

            if (fileResource == null) {
                log.warn("ISO 中未找到 " + type + " 资源: itemId=" + itemId);
                return null;
            }

            // 存入缓存，同时保存文件名元数据
            String fileName = fileResource.getFilename();
            cacheStorage.saveFile(cachePath, fileResource);
            assert fileName != null;
            saveFileName(cachePath, fileName);

            log.info("{} 已提取并缓存: itemId={}, type={}", LOG_PREFIX, itemId, type);
            return getCachedResource(cachePath);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清除指定启动项的全部缓存。
     *
     * @param itemId 启动项 ID
     * @throws IOException 如果删除失败
     */
    public void clearCache(Long itemId) throws IOException {
        cacheStorage.delete(itemId.toString());
        log.info("{} 已清除缓存: itemId={}", LOG_PREFIX, itemId);
    }

    /**
     * 尝试从已知精确路径列表中提取文件。
     *
     * @return 提取成功返回 Resource，全部失败返回 null
     */
    private Resource tryKnownPaths(Resource isoResource, List<String> knownPaths) {
        for (String path : knownPaths) {
            try {
                Resource resource = isoHandler.getResource(isoResource, path);
                if (resource != null) {
                    log.debug("{} Phase 1 命中: {}", LOG_PREFIX, path);
                    return resource;
                }
            } catch (IOException ignored) {
                // 该路径不存在，继续尝试下一个
            }
        }
        return null;
    }

    /**
     * 加载 ISO 启动项的 ISO 文件资源。
     * <p>使用公共网盘 UID（0L），与现有 PxeBootController.getBootItemFile 保持一致。</p>
     */
    private Resource loadIsoResource(BootItem item) throws IOException {
        String isoPath = item.getResourcePath();
        int lastSlash = isoPath.lastIndexOf('/');
        String isoDir = lastSlash >= 0 ? isoPath.substring(0, lastSlash) : "/";
        String isoFileName = lastSlash >= 0 ? isoPath.substring(lastSlash + 1) : isoPath;
        Resource isoResource = diskFileSystemManager.getMainFileSystem()
            .getResource(UserConstants.PUBLIC_USER_ID, isoDir, isoFileName);
        if (isoResource == null || !isoResource.exists()) {
            throw new IOException("ISO 文件不存在: " + isoPath);
        }
        return isoResource;
    }

    /**
     * 构建缓存路径。
     */
    private String buildCachePath(Long itemId, String key) {
        return itemId + "/" + key;
    }

    /**
     * 计算字符串的 SHA-256 哈希值，取前 16 位十六进制字符。
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 保存文件名到缓存元数据文件。
     */
    private void saveFileName(String cachePath, String fileName) throws IOException {
        cacheStorage.saveFile(cachePath + ".name", new org.springframework.core.io.ByteArrayResource(fileName.getBytes()));
    }

    /**
     * 从缓存元数据文件读取文件名，不存在时返回 null。
     */
    private String loadFileName(String cachePath) throws IOException {
        return cacheStorage.getFile(cachePath + ".name")
            .map(r -> {
                try {
                    return new String(r.getInputStream().readAllBytes());
                } catch (IOException e) {
                    return null;
                }
            })
            .orElse(null);
    }

    /**
     * 从缓存获取资源并包装文件名。
     */
    private Resource getCachedResource(String cachePath) throws IOException {
        return cacheStorage.getFile(cachePath)
            .map(r -> {
                try {
                    String fileName = loadFileName(cachePath);
                    return fileName != null ? (Resource) new NamedResource(r, fileName) : r;
                } catch (IOException e) {
                    return r;
                }
            })
            .orElse(null);
    }

    /**
     * 包装 Resource，提供正确的文件名。
     */
    private static class NamedResource extends AbstractResource {
        private final Resource delegate;
        private final String fileName;

        NamedResource(Resource delegate, String fileName) {
            this.delegate = delegate;
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public boolean exists() {
            return delegate.exists();
        }

        @Override
        public long contentLength() throws IOException {
            return delegate.contentLength();
        }

        @Override
        public String getDescription() {
            return "NamedResource [" + fileName + "]";
        }
    }
}
