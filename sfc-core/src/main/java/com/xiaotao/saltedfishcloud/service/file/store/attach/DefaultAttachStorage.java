package com.xiaotao.saltedfishcloud.service.file.store.attach;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 默认的附属存储实现。
 * <p>
 * 所有文件操作路径均会被约束在 {@code sys.store.root/attach/<storageDomainId>} 目录下。
 */
public class DefaultAttachStorage implements AttachStorage {

    private final StoreServiceFactory storeServiceFactory;
    private final String storageDomainRootPath;

    /**
     * 创建一个默认附属存储实例。
     *
     * @param storeServiceFactory 存储服务工厂
     * @param storageDomainRootPath 存储域根目录路径
     */
    public DefaultAttachStorage(StoreServiceFactory storeServiceFactory, String storageDomainRootPath) {
        this.storeServiceFactory = storeServiceFactory;
        this.storageDomainRootPath = storageDomainRootPath;
    }

    /**
     * 获取当前生效的底层原始存储操作器。
     *
     * @return 底层原始存储操作器
     */
    private Storage getStorageProvider() {
        return storeServiceFactory.getService().getStorageProvider();
    }

    /**
     * 获取当前存储域根目录。
     *
     * @return 存储域根目录
     */
    private String getStorageDomainRootPath() {
        return storageDomainRootPath;
    }

    /**
     * 将相对路径规范化为安全路径。
     * <p>
     * 允许路径中使用 {@code .} 和 {@code ..}，但不允许越过存储域根目录。
     *
     * @param path 原始相对路径
     * @return 规范化后的安全相对路径
     */
    private String normalizeRelativePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        String normalizedPath = path.replace('\\', '/').trim();
        String[] pathSegments = normalizedPath.split("/+");
        Deque<String> safeSegments = new ArrayDeque<>();
        for (String segment : pathSegments) {
            if (segment == null || segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (safeSegments.isEmpty()) {
                    throw new JsonException(400, "附属存储路径不能越过根目录：" + path);
                }
                safeSegments.removeLast();
            } else {
                safeSegments.addLast(segment);
            }
        }
        return String.join("/", safeSegments);
    }

    /**
     * 解析存储域内的目标路径。
     *
     * @param path 相对路径
     * @return 底层存储使用的完整路径
     */
    private String resolveStoragePath(String path) {
        String normalizedRelativePath = normalizeRelativePath(path);
        if (normalizedRelativePath.isEmpty()) {
            return getStorageDomainRootPath();
        }
        return StringUtils.appendPath(getStorageDomainRootPath(), normalizedRelativePath);
    }

    /**
     * 解析用于保存文件的目标相对路径。
     *
     * @param path 原始文件路径
     * @return 规范化后的相对路径
     */
    private String resolveRelativeFilePath(String path) {
        String normalizedRelativePath = normalizeRelativePath(path);
        if (normalizedRelativePath.isEmpty()) {
            throw new JsonException(400, "附属存储文件路径不能为空");
        }
        return normalizedRelativePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Resource> getFile(String path) throws IOException {
        Storage storageProvider = getStorageProvider();
        return Optional.ofNullable(storageProvider.getResource(resolveStoragePath(path)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long saveFile(String path, OutputStreamConsumer<OutputStream> outputStreamConsumer) throws IOException {
        if (outputStreamConsumer == null) {
            throw new JsonException(400, "输出流处理器不能为空");
        }
        String normalizedRelativePath = resolveRelativeFilePath(path);
        String targetPath = resolveStoragePath(normalizedRelativePath);
        String parentRelativePath = PathUtils.getParentPath(normalizedRelativePath);
        if (!parentRelativePath.isEmpty() && !exist(parentRelativePath)) {
            mkdir(parentRelativePath);
        }
        try (OutputStream os = getStorageProvider().newOutputStream(targetPath)) {
            return outputStreamConsumer.accept(os).size();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<List<FileInfo>> listFiles(String path) throws IOException {
        Storage storageProvider = getStorageProvider();
        String targetPath = resolveStoragePath(path);
        FileInfo fileInfo = storageProvider.getFileInfo(targetPath);
        if (fileInfo == null || !fileInfo.isDir()) {
            return Optional.empty();
        }
        return Optional.of(new ArrayList<>(storageProvider.listFiles(targetPath)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exist(String path) throws IOException {
        return getStorageProvider().exist(resolveStoragePath(path));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String path) throws IOException {
        getStorageProvider().delete(resolveStoragePath(path));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mkdir(String path) throws IOException {
        getStorageProvider().mkdirs(resolveStoragePath(path));
    }
}


