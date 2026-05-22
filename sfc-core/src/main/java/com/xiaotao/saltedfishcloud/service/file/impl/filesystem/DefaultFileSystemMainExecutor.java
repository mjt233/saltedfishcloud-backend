package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.cache.LockFactory;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.model.progress.event.UpdateFileRecordCompleteEvent;
import com.xiaotao.saltedfishcloud.model.progress.event.UpdateFileRecordStartEvent;
import com.xiaotao.saltedfishcloud.utils.DiskFileSystemUtils;
import com.xiaotao.saltedfishcloud.utils.LockUtils;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StreamUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

/**
 * 默认主文件系统执行组件，负责主存储路径下的实际业务处理。
 */
@Component
public class DefaultFileSystemMainExecutor {
    /**
     * 文件系统日志前缀。
     */
    private static final String LOG_PREFIX = "[FileSystem]";

    /**
     * 元数据操作组件。
     */
    @Autowired
    private DefaultFileSystemMetadataOperator metadataOperator;

    /**
     * 主存储执行组件。
     */
    @Autowired
    private DefaultFileSystemStoreOperator storeOperator;

    /**
     * 分布式锁工厂。
     */
    @Autowired
    private LockFactory lockFactory;

    /**
     * 获取写文件时用到的分布式锁key。
     *
     * @param uid 用户ID
     * @param path 文件所在路径
     * @param name 文件名
     * @return 分布式锁key
     */
    private static String getStoreLockKey(long uid, String path, String name) {
        return uid + ":" + StringUtils.appendPath(path, name);
    }

    /**
     * 获取缩略图。
     *
     * @param uid 用户ID
     * @param path 文件所在目录
     * @param name 文件名
     * @return 缩略图资源
     */
    public Resource getThumbnail(long uid, String path, String name) throws IOException {
        return storeOperator.getThumbnail(uid, path, name);
    }

    /**
     * 主文件系统更新时间属性。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param names 文件名列表
     * @param attribute 时间属性
     */
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        FileInfo dirInfo = metadataOperator.getByPath(uid, path).orElse(null);
        if (dirInfo == null || dirInfo.isFile()) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("{} 找不到路径{}:{}对应的节点，无法修改文件日期信息", LOG_PREFIX, uid, path);
            return;
        }
        List<FileInfo> fileInfoList = metadataOperator.findByUidAndNodeId(uid, dirInfo.getNode(), names);
        fileInfoList.forEach(fileInfo -> {
            if (attribute.apply(fileInfo)) {
                metadataOperator.save(fileInfo);
            }
        });
        storeOperator.updateTime(uid, path, names, attribute);
    }

    /**
     * 执行主文件系统 quickSave。
     *
     * @param uid 用户ID
     * @param path 目标目录
     * @param name 文件名
     * @param md5 文件MD5
     * @return 成功返回true，否则返回false
     */
    public boolean quickSave(long uid, String path, String name, String md5) throws IOException {
        List<FileInfo> files = metadataOperator.getFileInfoByMd5(md5, 1);
        if (files.isEmpty()) {
            return false;
        }
        FileInfo existMd5File = ObjectUtils.clone(files.get(0), FileInfo::new);
        String filePath = metadataOperator.getPathByNodeId(existMd5File.getUid(), existMd5File.getNode()).orElse(null);
        if (filePath == null) {
            return false;
        }
        Resource resource = getResource(existMd5File.getUid(), filePath, existMd5File.getName());
        if (resource == null) {
            return false;
        }
        try {
            FileInfo newFile = FileInfo.createFrom(existMd5File, false);
            newFile.setId(null);
            newFile.setUid(uid);
            newFile.setStreamSource(resource);
            newFile.setName(name);
            if (!storeOperator.isUniqueStore()) {
                saveFileByStream(newFile, path, os -> DiskFileSystemUtils.saveFile(newFile, os));
            } else {
                metadataOperator.saveRecord(newFile, path);
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * 判断主文件系统路径是否存在。
     *
     * @param uid 用户ID
     * @param path 完整路径
     * @return 存在返回true，否则返回false
     */
    public boolean exist(long uid, String path) {
        return metadataOperator.exist(uid, path);
    }

    /**
     * 获取主文件系统资源。
     *
     * @param uid 用户ID
     * @param path 文件所在目录
     * @param name 文件名
     * @return 文件资源
     */
    public Resource getResource(long uid, String path, String name) throws IOException {
        Resource resource = storeOperator.getResource(uid, path, name);
        if (resource == null) {
            return null;
        }
        return ResourceUtils.bindFileInfo(resource, () -> metadataOperator.getFileInfo(uid, path, name));
    }

    /**
     * 创建主文件系统目录树。
     *
     * @param uid 用户ID
     * @param path 完整目录路径
     * @return 创建后的节点ID
     */
    public String mkdirs(long uid, String path) throws IOException {
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        if (pb.getPath().isEmpty()) {
            return String.valueOf(uid);
        }
        String parent = pb.range(-1);
        String name = pb.range(1, -1);
        if (getResource(uid, parent, name) != null) {
            throw new UnsupportedOperationException("已存在同名文件：" + path);
        }
        String nodeId = metadataOperator.getAndMkdirs(uid, path, false);
        storeOperator.ensureDirectory(uid, path);
        return nodeId;
    }

    /**
     * 复制主文件系统中的文件与记录。
     *
     * @param param 复制参数
     * @param callback 复制回调
     */
    public void copy(SimpleFileTransferParam param, @Nullable FileTransferCallback callback) throws IOException {
        FileTransferItem transferItem = FileTransferItem.builder()
                .from(param.getSourcePath())
                .to(param.getTargetPath())
                .build();
        if (callback != null) {
            callback.onAdditionalEvent(UpdateFileRecordStartEvent.of());
        }
        metadataOperator.copy(param, callback);
        if (callback != null) {
            callback.onAdditionalEvent(UpdateFileRecordCompleteEvent.of(transferItem));
        }
        storeOperator.copy(param, callback);
    }

    /**
     * 移动主文件系统中的文件。
     *
     * @param uid 用户ID
     * @param source 原目录
     * @param target 目标目录
     * @param name 文件名
     * @param overwrite 是否覆盖
     */
    public void move(long uid, String source, String target, String name, boolean overwrite) throws IOException {
        Lock lock = lockFactory.getLock(getStoreLockKey(uid, target, name));
        try {
            lock.lock();
            String decodedTarget = URLDecoder.decode(target, StandardCharsets.UTF_8);
            metadataOperator.move(uid, source, decodedTarget, name, overwrite);
            storeOperator.move(uid, source, decodedTarget, name, overwrite);
        } catch (DuplicateKeyException e) {
            throw new JsonException(409, "目标目录下已存在 " + name + " 暂不支持目录合并或移动覆盖");
        } catch (UnsupportedEncodingException e) {
            throw new JsonException(400, "不支持的编码（请使用UTF-8）");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 按路径列出主文件系统中的文件。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @return 文件列表数组
     */
    public List<FileInfo>[] getUserFileList(long uid, String path) {
        return metadataOperator.getUserFileList(uid, path);
    }

    /**
     * 按路径和文件名列出主文件系统中的文件。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param nameList 文件名列表
     * @return 文件信息列表
     */
    public List<FileInfo> getUserFileList(long uid, String path, @Nullable Collection<String> nameList) {
        return metadataOperator.getUserFileList(uid, path, nameList);
    }

    /**
     * 采集主文件系统全部目录结构。
     *
     * @param uid 用户ID
     * @param reverse 是否倒序
     * @return 目录到文件列表映射
     */
    public LinkedHashMap<String, List<FileInfo>> collectFiles(long uid, boolean reverse) {
        return metadataOperator.collectFiles(uid, reverse);
    }

    /**
     * 按节点ID列出主文件系统中的文件。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @return 文件列表数组
     */
    public List<FileInfo>[] getUserFileListByNodeId(long uid, String nodeId) {
        return metadataOperator.getUserFileListByNodeId(uid, nodeId);
    }

    /**
     * 搜索主文件系统中的文件。
     *
     * @param uid 用户ID
     * @param key 搜索关键字
     * @param page 页码
     * @param size 页大小
     * @return 搜索结果
     */
    public CommonPageInfo<FileInfo> search(long uid, String key, Integer page, Integer size) {
        return metadataOperator.search(uid, key, page, size);
    }

    /**
     * 通过移动本地文件的方式保存到主文件系统。
     *
     * @param uid 用户ID
     * @param nativeFilePath 本地文件路径
     * @param path 目标目录路径
     * @param fileInfo 文件信息
     */
    public void moveToSaveFile(long uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        Lock lock = lockFactory.getLock(getStoreLockKey(uid, path, fileInfo.getName()));
        try {
            lock.lock();
            fileInfo.setUid(uid);
            storeOperator.moveToSave(uid, nativeFilePath, path, fileInfo);
            metadataOperator.saveRecord(fileInfo, path);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 批量保存主文件系统中的文件。
     *
     * @param fileInfos 文件列表
     * @param callback 传输回调
     */
    public void batchSaveFiles(List<FileInfo> fileInfos, @Nullable FileTransferCallback callback) throws IOException {
        if (fileInfos == null || fileInfos.isEmpty()) {
            return;
        }
        record SaveKey(Long uid, String path) {}

        java.util.LinkedHashMap<SaveKey, List<FileInfo>> groupedFiles = new java.util.LinkedHashMap<>();
        for (FileInfo fileInfo : fileInfos) {
            if (fileInfo == null
                    || fileInfo.getUid() == null
                    || fileInfo.getPath() == null
                    || fileInfo.getName() == null
                    || fileInfo.getStreamSource() == null) {
                throw new IllegalArgumentException("batchSaveFiles param invalid: uid/path/name/streamSource must not be null");
            }
            groupedFiles.computeIfAbsent(new SaveKey(fileInfo.getUid(), fileInfo.getPath()), item -> new ArrayList<>()).add(fileInfo);
        }

        for (java.util.Map.Entry<SaveKey, List<FileInfo>> entry : groupedFiles.entrySet()) {
            SaveKey key = entry.getKey();
            List<FileInfo> group = entry.getValue();
            for (FileInfo fileInfo : group) {
                if (callback != null && callback.shouldInterrupt()) {
                    return;
                }
                FileTransferItem item = FileTransferItem.builder()
                        .from(fileInfo.getName())
                        .to(StringUtils.appendPath(key.path(), fileInfo.getName()))
                        .total(fileInfo.getSize())
                        .loaded(0L)
                        .fileInfo(fileInfo)
                        .build();
                if (callback != null) {
                    callback.onFileStart(item);
                }
                LockUtils.execute(getStoreLockKey(key.uid(), key.path(), fileInfo.getName()),
                        () -> storeBySource(fileInfo, key.path(), item));
                item.setLoaded(item.getTotal());
                if (callback != null) {
                    callback.onFileComplete(item);
                }
            }
            metadataOperator.batchSaveFileInSameDirectory(key.uid(), key.path(), group);
        }
    }

    /**
     * 将源文件流写入主存储，并更新传输进度。
     *
     * @param fileInfo 文件信息
     * @param savePath 目标目录路径
     * @param item 传输项
     */
    private void storeBySource(FileInfo fileInfo, String savePath, FileTransferItem item) throws IOException {
        storeOperator.storeByStream(fileInfo, savePath, os -> {
            try (InputStream is = fileInfo.getStreamSource().getInputStream()) {
                return StreamUtils.copyStreamAndComputeMd5(is, os, fileInfo.getMd5(), (buf, len) -> {
                    Long currentLoaded = item.getLoaded();
                    if (currentLoaded == null || currentLoaded < 0) {
                        currentLoaded = 0L;
                    }
                    item.setLoaded(currentLoaded + len);
                }).applyTo(fileInfo);
            }
        });
    }

    /**
     * 通过流写入主文件系统。
     *
     * @param file 文件信息
     * @param savePath 目标目录路径
     * @param streamConsumer 输出流消费函数
     */
    public void saveFileByStream(FileInfo file, String savePath, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        Long uid = file.getUid();
        String nodeId = metadataOperator.getNodeIdByPath(uid, savePath).orElseGet(() -> {
            try {
                return mkdirs(uid, savePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        LockUtils.execute(getStoreLockKey(uid, savePath, file.getName()), () -> {
            FileInfo originInfo = metadataOperator.getFileInfoByNode(uid, nodeId, file.getName());
            if (originInfo != null) {
                if (originInfo.isDir()) {
                    throw new IllegalArgumentException("无法使用文件来覆盖文件夹 path: " + savePath + " name:" + file.getName());
                }
                FileInfo tmpFile = new FileInfo();
                BeanUtils.copyProperties(file, tmpFile);
                tmpFile.setName(tmpFile.getName() + "." + IdUtil.getId() + ".tmp");

                storeOperator.storeByStream(tmpFile, savePath, streamConsumer);
                metadataOperator.saveRecord(tmpFile, savePath);

                deleteFile(uid, savePath, Collections.singletonList(file.getName()));
                rename(file.getUid(), savePath, tmpFile.getName(), file.getName());
            } else {
                storeOperator.storeByStream(file, savePath, streamConsumer);
                metadataOperator.saveRecord(file, savePath);
            }
        });
    }

    /**
     * 创建主文件系统目录。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param name 目录名
     */
    public void mkdir(long uid, String path, String name) throws IOException {
        if (!storeOperator.mkdir(uid, path, name)) {
            throw new IOException("在" + path + "创建文件夹失败");
        }
        metadataOperator.mkdirs(uid, StringUtils.appendPath(path, name), false);
    }

    /**
     * 删除主文件系统中的文件。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param names 文件名列表
     * @return 删除数量
     */
    public long deleteFile(long uid, String path, List<String> names) throws IOException {
        ArrayList<Lock> locks = new ArrayList<>();
        for (String fileName : names) {
            Lock lock = lockFactory.getLock(getStoreLockKey(uid, path, fileName));
            lock.lock();
            locks.add(lock);
        }
        try {
            List<FileInfo> fileInfos = metadataOperator.deleteRecords(uid, path, names);
            return storeOperator.delete(uid, path, names, fileInfos);
        } finally {
            for (Lock lock : locks) {
                lock.unlock();
            }
        }
    }

    /**
     * 重命名主文件系统中的文件。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param name 原文件名
     * @param newName 新文件名
     */
    public void rename(long uid, String path, String name, String newName) throws IOException {
        Lock sourceLock = lockFactory.getLock(getStoreLockKey(uid, path, name));
        Lock targetLock = lockFactory.getLock(getStoreLockKey(uid, path, newName));
        sourceLock.lock();
        targetLock.lock();
        try {
            metadataOperator.rename(uid, path, name, newName);
            storeOperator.rename(uid, path, name, newName);
        } finally {
            sourceLock.unlock();
            targetLock.unlock();
        }
    }

    /**
     * 获取主文件系统状态。
     *
     * @return 文件系统状态列表
     */
    public List<FileSystemStatus> getStatus() {
        return metadataOperator.buildStatus(storeOperator.getStatus());
    }
}
