package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEvent;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEventLevel;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认主存储执行组件，负责与底层存储服务交互，不直接处理文件记录。
 */
@Component
public class DefaultFileSystemStoreOperator {
    /**
     * 存储服务工厂。
     */
    @Autowired
    private StoreServiceFactory storeServiceFactory;

    /**
     * 资源MD5解析器。
     */
    @Autowired
    private FileResourceMd5Resolver md5Resolver;

    /**
     * 缩略图服务。
     */
    @Autowired
    private ThumbnailService thumbnailService;

    /**
     * 获取当前主存储服务。
     *
     * @return 当前主存储服务
     */
    public StoreService getStoreService() {
        return storeServiceFactory.getService();
    }

    /**
     * 判断当前是否为唯一存储模式。
     *
     * @return 唯一存储模式返回true，否则返回false
     */
    public boolean isUniqueStore() {
        return getStoreService().isUnique();
    }

    /**
     * 获取指定文件的缩略图资源。
     *
     * @param uid 用户ID
     * @param path 文件所在目录
     * @param name 文件名
     * @return 缩略图资源，不存在时返回null
     */
    public Resource getThumbnail(long uid, String path, String name) throws IOException {
        String md5 = md5Resolver.getResourceMd5(uid, StringUtils.appendPath(path, name));
        if (md5 != null) {
            return thumbnailService.getThumbnail(md5, FileUtils.getSuffix(name));
        }
        return null;
    }

    /**
     * 获取底层存储中的文件资源。
     *
     * @param uid 用户ID
     * @param path 文件所在目录
     * @param name 文件名
     * @return 文件资源，不存在时返回null
     */
    public Resource getResource(long uid, String path, String name) throws IOException {
        return getStoreService().getResource(uid, path, name);
    }

    /**
     * 确保原始存储模式下的物理目录存在。
     *
     * @param uid 用户ID
     * @param path 目录路径
     */
    public void ensureDirectory(long uid, String path) throws IOException {
        StoreService storeService = getStoreService();
        if (!storeService.isUnique() && StringUtils.hasText(path)) {
            storeService.mkdir(uid, path, "");
        }
    }

    /**
     * 复制主存储中的物理数据。
     *
     * @param param 复制参数
     * @param callback 复制回调，可为null
     */
    public void copy(SimpleFileTransferParam param, @Nullable FileTransferCallback callback) throws IOException {
        StoreService storeService = getStoreService();
        if (!storeService.isUnique()) {
            if (callback != null) {
                callback.onAdditionalEvent(new CopyProgressEvent() {
                    {
                        setMessage("哈希存储模式下，物理存储无需操作，已跳过");
                        setName("Store Skip");
                    }

                    @Override
                    public CopyProgressEventLevel getLevel() {
                        return CopyProgressEventLevel.INFO;
                    }
                });
            }
            storeService.copy(param, callback);
        }
    }

    /**
     * 通过本地文件移动的方式写入物理存储。
     *
     * @param uid 用户ID
     * @param nativeFilePath 本地文件路径
     * @param path 网盘目录路径
     * @param fileInfo 文件信息
     */
    public void moveToSave(long uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        getStoreService().moveToSave(uid, nativeFilePath, path, fileInfo);
    }

    /**
     * 通过输出流消费函数将文件写入物理存储。
     *
     * @param file 文件信息
     * @param savePath 保存路径
     * @param streamConsumer 输出流消费函数
     */
    public void storeByStream(FileInfo file, String savePath, com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        getStoreService().storeByStream(file, savePath, streamConsumer);
    }

    /**
     * 创建物理目录。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param name 目录名称
     * @return 创建成功返回true，唯一存储模式下恒为true
     */
    public boolean mkdir(long uid, String path, String name) throws IOException {
        StoreService storeService = getStoreService();
        return storeService.isUnique() || storeService.mkdir(uid, path, name);
    }

    /**
     * 删除物理存储中的文件内容。
     *
     * @param uid 用户ID
     * @param path 文件所在目录
     * @param names 文件名列表
     * @param fileInfos 已删除的文件记录列表
     * @return 删除数量
     */
    public long delete(long uid, String path, Collection<String> names, List<FileInfo> fileInfos) throws IOException {
        long result = 0L;
        StoreService storeService = getStoreService();
        if (storeService.isUnique()) {
            Map<String, List<FileInfo>> md5FileGroup = fileInfos.stream()
                    .filter(FileInfo::isFile)
                    .collect(Collectors.groupingBy(FileInfo::getMd5));
            Set<String> inRefMd5Set = CollectionUtils.partition(md5FileGroup.keySet(), 500)
                    .stream()
                    .flatMap(md5List -> md5Resolver.checkHasRef(md5List).stream())
                    .collect(Collectors.toSet());
            List<String> toDeleteMd5 = md5FileGroup.keySet()
                    .stream()
                    .filter(md5 -> !inRefMd5Set.contains(md5))
                    .toList();
            for (String md5 : toDeleteMd5) {
                storeService.delete(md5);
            }
        } else {
            result += storeService.delete(uid, path, names);
        }
        return result;
    }

    /**
     * 移动物理存储中的文件。
     *
     * @param sourceUid 源用户ID
     * @param source 原目录
     * @param target 目标目录
     * @param name 文件名
     * @param overwrite 是否覆盖
     * @param targetUid 目标用户ID
     */
    public void move(long sourceUid, String source, long targetUid, String target, String name, boolean overwrite) throws IOException {
        StoreService storeService = getStoreService();
        if (!storeService.isUnique()) {
            storeService.move(sourceUid, source, targetUid, target, name, overwrite);
        }
    }

    /**
     * 重命名物理存储中的文件。
     *
     * @param uid 用户ID
     * @param path 文件所在目录
     * @param name 原文件名
     * @param newName 新文件名
     */
    public void rename(long uid, String path, String name, String newName) throws IOException {
        StoreService storeService = getStoreService();
        if (!storeService.isUnique()) {
            storeService.rename(uid, path, name, newName);
        }
    }

    /**
     * 更新物理存储中的文件时间属性。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param names 文件名列表
     * @param attribute 时间属性
     */
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        StoreService storeService = getStoreService();
        if (!storeService.isUnique()) {
            storeService.updateTime(uid, path, names, attribute);
        }
    }

    /**
     * 获取底层存储状态。
     *
     * @return 底层存储状态列表
     */
    public List<FileSystemStatus> getStatus() {
        return getStoreService().getStatus();
    }
}
