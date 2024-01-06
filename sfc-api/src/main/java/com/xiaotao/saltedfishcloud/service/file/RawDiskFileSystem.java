package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveHandler;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

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
            throw new IllegalArgumentException("[" + basePath + "]为无效路径");
        }
        this.storeHandler = storeHandler;
        camHandler = CopyAndMoveHandler.createByStoreHandler(storeHandler);
        this.basePath = basePath;
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
    public Resource getResourceByMd5(String md5) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public void copy(long uid, String source, String target, long targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException {
        if (uid != targetUid) {
            throw new UnsupportedOperationException("不支持跨用户网盘复制");
        }

        camHandler.copy(StringUtils.appendPath(basePath, source, sourceName), StringUtils.appendPath(basePath, target, targetName), overwrite);
    }

    @Override
    public void move(long uid, String source, String target, String name, boolean overwrite) throws IOException {
        camHandler.copy(StringUtils.appendPath(basePath, source, name), StringUtils.appendPath(basePath, target, name), overwrite);
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
    public void moveToSaveFile(long uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        if (!Files.exists(nativeFilePath) || Files.isDirectory(nativeFilePath)) {
            throw new IllegalArgumentException(nativeFilePath + " 不是文件或不存在");
        }
        try (InputStream is = Files.newInputStream(nativeFilePath)) {
            storeHandler.store(StringUtils.appendPath(basePath, path, fileInfo.getName()), Files.size(nativeFilePath), is);
        }

    }

    @Override
    public long saveFile(long uid, InputStream stream, String path, FileInfo fileInfo) throws IOException {
        return storeHandler.store(StringUtils.appendPath(basePath, path, fileInfo.getName()), fileInfo.getSize(), stream);
    }

    @Override
    public long saveFile(long uid, MultipartFile file, String requestPath, String md5) throws IOException {
        long size = file.getSize();
        storeHandler.store(StringUtils.appendPath(basePath, requestPath, file.getOriginalFilename()), size, file.getInputStream());
        return size;
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
}
