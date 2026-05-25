package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveHandler;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveProperty;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 将作用域化后的 {@link Storage} 适配为 {@link DiskFileSystem}，用于挂载点委托文件系统场景。
 */
public class StorageDiskFileSystemAdapter implements DiskFileSystem, Closeable {
    /**
     * 底层存储实现。
     */
    @Getter
    private final Storage storage;

    /**
     * 基于底层存储构建的复制/移动处理器。
     */
    @Getter
    private final CopyAndMoveHandler copyAndMoveHandler;

    /**
     * 可选的缩略图服务。
     */
    @Setter
    private ThumbnailService thumbnailService;

    /**
     * 使用默认复制/移动配置创建存储文件系统适配器。
     *
     * @param storage 已完成作用域包装的底层存储
     */
    public StorageDiskFileSystemAdapter(Storage storage) {
        this(storage, CopyAndMoveProperty.builder().build());
    }

    /**
     * 使用指定复制/移动配置创建存储文件系统适配器。
     *
     * @param storage 已完成作用域包装的底层存储
     * @param property 复制/移动配置
     */
    public StorageDiskFileSystemAdapter(Storage storage, CopyAndMoveProperty property) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.copyAndMoveHandler = CopyAndMoveHandler.createByStoreHandler(storage, Objects.requireNonNull(property, "property must not be null"));
    }

    @Override
    public List<FileInfo> getUserFileList(long uid, String path, @Nullable Collection<String> nameList) throws IOException {
        if (CollectionUtils.isEmpty(nameList)) {
            List<FileInfo>[] userFileList = getUserFileList(uid, path);
            if (userFileList == null) {
                return null;
            }
            return Stream.concat(userFileList[0].stream(), userFileList[1].stream()).collect(Collectors.toList());
        }
        Set<String> nameSet = new HashSet<>(nameList);
        return storage.listFiles(path)
                .stream()
                .filter(fileInfo -> nameSet.contains(fileInfo.getName()))
                .toList();
    }

    @Override
    public Resource getThumbnail(long uid, String path, String name) throws IOException {
        if (thumbnailService == null) {
            return null;
        }
        String suffix = FileUtils.getSuffix(name);
        if (!thumbnailService.isSupport(suffix)) {
            return null;
        }
        Resource resource = getResource(uid, path, name);
        if (resource == null) {
            return null;
        }
        String lastModified = String.valueOf(resource.lastModified());
        return thumbnailService.getThumbnail(resource, suffix, SecureUtils.getMd5(StringUtils.appendPath(path, name, lastModified)));
    }

    @Override
    public boolean quickSave(long uid, String path, String name, String md5) {
        return false;
    }

    @Override
    public boolean exist(long uid, String path) throws IOException {
        return storage.exist(path);
    }

    @Override
    public Resource getResource(long uid, String path, String name) throws IOException {
        return storage.getResource(StringUtils.appendPath(path, name));
    }

    @Override
    public String mkdirs(long uid, String path) throws IOException {
        storage.mkdirs(path);
        return null;
    }

    @Override
    public void copy(SimpleFileTransferParam param, FileTransferCallback callback) throws IOException {
        if (param.getSourceUid() == null || param.getTargetUid() == null) {
            throw new IllegalArgumentException("sourceUid 和 targetUid 不能为空");
        }
        if (!param.getSourceUid().equals(param.getTargetUid())) {
            throw new UnsupportedOperationException("默认的挂载适配器不支持跨用户网盘复制");
        }

        List<String> files = param.getFiles();
        if (files == null || files.isEmpty()) {
            files = storage.listFiles(param.getSourcePath()).stream().map(FileInfo::getName).toList();
        }

        boolean overwrite = Boolean.TRUE.equals(param.getIsOverwrite());
        String sourcePath = param.getSourcePath();
        String targetPath = param.getTargetPath();
        for (String fileName : files) {
            if (callback != null && callback.shouldInterrupt()) {
                return;
            }
            copyAndMoveHandler.copy(
                    StringUtils.appendPath(sourcePath, fileName),
                    StringUtils.appendPath(targetPath, fileName),
                    overwrite,
                    callback
            );
        }
    }

    @Override
    public void move(long sourceUid, String source, long targetUid, String target, String name, boolean overwrite) throws IOException {
        copyAndMoveHandler.move(StringUtils.appendPath(source, name), StringUtils.appendPath(target, name), overwrite);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FileInfo>[] getUserFileList(long uid, String path) throws IOException {
        List<FileInfo> fileInfos = storage.listFiles(path);
        if (fileInfos == null) {
            return null;
        }
        List<FileInfo>[] result = new List[2];
        result[0] = fileInfos.stream().filter(FileInfo::isDir).collect(Collectors.toList());
        result[1] = fileInfos.stream().filter(FileInfo::isFile).collect(Collectors.toList());
        return result;
    }

    @Override
    public CommonPageInfo<FileInfo> search(long uid, String key, Integer page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveFileByStream(FileInfo file, String savePath, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        String finalTargetPath = StringUtils.appendPath(savePath, file.getName());
        String tmpPath = finalTargetPath + "." + IdUtil.getId() + ".tmp";
        boolean success = false;
        try (OutputStream outputStream = storage.newOutputStream(tmpPath)) {
            streamConsumer.accept(outputStream).applyTo(file);
            outputStream.close();
            success = true;
            if (!storage.move(tmpPath, finalTargetPath, null)) {
                if (storage.exist(finalTargetPath)) {
                    storage.delete(finalTargetPath);
                }
                storage.move(tmpPath, finalTargetPath, null);
            }
        } finally {
            if (!success) {
                storage.delete(tmpPath);
            }
        }
    }

    @Override
    public void mkdir(long uid, String path, String name) throws IOException {
        storage.mkdir(StringUtils.appendPath(path, name));
    }

    @Override
    public long deleteFile(long uid, String path, List<String> name) throws IOException {
        for (String item : name) {
            storage.delete(StringUtils.appendPath(path, item));
        }
        return name.size();
    }

    @Override
    public void rename(long uid, String path, String name, String newName) throws IOException {
        storage.rename(StringUtils.appendPath(path, name), newName);
    }

    @Override
    public void close() throws IOException {
        try {
            storage.close();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("关闭存储失败", e);
        }
    }

    @Override
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        storage.updateTime(path, names, attribute);
    }

    /**
     * 基于底层存储实例判断两个适配器是否等价。
     *
     * @param obj 待比较对象
     * @return 是否等价
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StorageDiskFileSystemAdapter other)) {
            return false;
        }
        return storage == other.storage;
    }

    /**
     * 计算基于底层存储实例的哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Integer.hashCode(System.identityHashCode(storage));
    }
}
