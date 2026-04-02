package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressCallback;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveHandler;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveProperty;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 原始的文件系统，根据DirectRawStoreHandler和basePath对存储操作器进行操作封装，暴露为对网盘文件系统的操作。
 * 需要手动设置单独的ThumbnailService以提供缩略图服务
 */
public class RawDiskFileSystem implements DiskFileSystem, Closeable {
    @Getter
    private final DirectRawStoreHandler storeHandler;

    @Getter
    private final CopyAndMoveHandler camHandler;

    @Getter
    private final String basePath;

    @Setter
    private ThumbnailService thumbnailService;

    /**
     * 将直接存储操作器封装为文件系统操作
     * @param storeHandler  存储操作器
     * @param basePath      统一给所有操作添加的路径前缀
     */
    public RawDiskFileSystem(DirectRawStoreHandler storeHandler, String basePath) throws IOException {
        if ("".equals(basePath)) {
            basePath = ".";
        }
        if(!storeHandler.exist(basePath)) {
            throw new IllegalArgumentException("文件系统创建失败，无法验证路径有效：" + basePath);
        }
        this.storeHandler = storeHandler;
        camHandler = CopyAndMoveHandler.createByStoreHandler(storeHandler, CopyAndMoveProperty.builder().build());
        this.basePath = basePath;
    }

    /**
     * 将直接存储操作器封装为文件系统操作
     * @param storeHandler  存储操作器
     * @param basePath      统一给所有操作添加的路径前缀
     */
    public RawDiskFileSystem(DirectRawStoreHandler storeHandler, String basePath, CopyAndMoveProperty property) throws IOException {
        if ("".equals(basePath)) {
            basePath = ".";
        }
        if(!storeHandler.exist(basePath)) {
            throw new IllegalArgumentException("文件系统创建失败，无法验证路径有效：" + basePath);
        }
        this.storeHandler = storeHandler;
        camHandler = CopyAndMoveHandler.createByStoreHandler(storeHandler, property);
        this.basePath = basePath;
    }

    @Override
    public List<FileInfo> getUserFileList(long uid, String path, @Nullable Collection<String> nameList) throws IOException {
        if (CollectionUtils.isEmpty(nameList)) {
            List<FileInfo>[] userFileList = getUserFileList(uid, path);
            if (userFileList == null) {
                return null;
            } else {
                return Stream.concat(
                        userFileList[0].stream(),
                        userFileList[1].stream()
                ).collect(Collectors.toList());
            }
        }
        Set<String> nameSet = new HashSet<>(nameList);
        return storeHandler.listFiles(StringUtils.appendPath(basePath, path))
                .stream()
                .filter(f -> nameSet.contains(f.getName()))
                .toList();
    }

    @Override
    public Resource getThumbnail(long uid, String path, String name) throws IOException {
        if (thumbnailService == null) {
            return null;
        }
        String suffix = FileUtils.getSuffix(name);
        if(!thumbnailService.isSupport(suffix)) {
            return null;
        }

        Resource resource = getResource(uid, path, name);
        if (resource == null) {
            return null;
        }
        String lastModified = String.valueOf(resource.lastModified());
        return thumbnailService.getThumbnail(resource, suffix, SecureUtils.getMd5(StringUtils.appendPath(path,name, lastModified)));
    }


    @Override
    public Resource getAvatar(long uid) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public void saveAvatar(long uid, Resource resource) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public boolean quickSave(long uid, String path, String name, String md5) throws IOException {
        return false;
    }

    @Override
    public boolean exist(long uid, String path) throws IOException {
        return storeHandler.exist(StringUtils.appendPath(basePath, path));
    }

    @Override
    public Resource getResource(long uid, String path, String name) throws IOException {
        return storeHandler.getResource(StringUtils.appendPath(basePath, path, name));
    }

    @Override
    public String mkdirs(long uid, String path) throws IOException {
        storeHandler.mkdirs(StringUtils.appendPath(basePath, path));
        return null;
    }

    @Override
    public void copy(SimpleFileTransferParam param, CopyProgressCallback callback) throws IOException {
        if (param.getSourceUid() == null || param.getTargetUid() == null) {
            throw new IllegalArgumentException("sourceUid 和 targetUid 不能为空");
        }
        if (!param.getSourceUid().equals(param.getTargetUid())) {
            throw new UnsupportedOperationException("默认的RawDiskFileSystem不支持跨用户网盘复制");
        }

        List<String> files = param.getFiles();
        if (files == null || files.isEmpty()) {
            files = storeHandler.listFiles(param.getSourcePath()).stream().map(FileInfo::getName).toList();
        }

        boolean overwrite = Boolean.TRUE.equals(param.getIsOverwrite());
        String sourcePath = param.getSourcePath();
        String targetPath = param.getTargetPath();

        for (String fileName : files) {
            // 检查是否被中断
            if (callback != null && callback.shouldInterrupt()) {
                return;
            }

            camHandler.copy(
                    StringUtils.appendPath(basePath, sourcePath, fileName),
                    StringUtils.appendPath(basePath, targetPath, fileName),
                    overwrite,
                    callback
            );
        }
    }

    @Override
    public void move(long uid, String source, String target, String name, boolean overwrite) throws IOException {
        camHandler.move(StringUtils.appendPath(basePath, source, name), StringUtils.appendPath(basePath, target, name), overwrite);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FileInfo>[] getUserFileList(long uid, String path) throws IOException {
        List<FileInfo> fileInfos = storeHandler.listFiles(StringUtils.appendPath(basePath, path));
        if (fileInfos == null) {
            return null;
        }
        List<FileInfo>[] res = new List[2];
        res[0] = fileInfos.stream().filter(FileInfo::isDir).collect(Collectors.toList());
        res[1] = fileInfos.stream().filter(FileInfo::isFile).collect(Collectors.toList());
        return res;
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(long uid, boolean reverse) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileInfo>[] getUserFileListByNodeId(long uid, String nodeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileInfo> search(long uid, String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveFileByStream(FileInfo file, String savePath, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        String finalTargetPath = StringUtils.appendPath(basePath, savePath, file.getName());
        String tmpPath = finalTargetPath + "." + IdUtil.getId() + ".tmp";
        boolean isSuccess = false;
        try(OutputStream os = storeHandler.newOutputStream(tmpPath)) {
            streamConsumer.accept(os).applyTo(file);
            os.close();
            isSuccess = true;
            storeHandler.move(tmpPath, finalTargetPath);
        } finally {
            if (!isSuccess) {
                storeHandler.delete(tmpPath);
            }
        }
    }

    @Override
    public void mkdir(long uid, String path, String name) throws IOException {
        storeHandler.mkdir(StringUtils.appendPath(basePath, path, name));
    }

    @Override
    public long deleteFile(long uid, String path, List<String> name) throws IOException {
        for (String n : name) {
            storeHandler.delete(StringUtils.appendPath(basePath, path, n));
        }
        return name.size();
    }

    @Override
    public void rename(long uid, String path, String name, String newName) throws IOException {
        storeHandler.rename(StringUtils.appendPath(basePath, path, name), newName);
    }

    @Override
    public void close() throws IOException {
        if (storeHandler instanceof Closeable) {
            ((Closeable) storeHandler).close();
        }
    }

    @Override
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        storeHandler.updateTime(StringUtils.appendPath(basePath, path), names, attribute);
    }
}
