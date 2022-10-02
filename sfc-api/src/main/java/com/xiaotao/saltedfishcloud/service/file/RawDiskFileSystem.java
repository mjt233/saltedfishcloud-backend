package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.model.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveHandler;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class RawDiskFileSystem implements DiskFileSystem {
    private final DirectRawStoreHandler storeHandler;
    private final CopyAndMoveHandler camHandler;
    private final String basePath;

    public RawDiskFileSystem(DirectRawStoreHandler storeHandler, String basePath) {
        this.storeHandler = storeHandler;
        camHandler = CopyAndMoveHandler.createByStoreHandler(storeHandler);
        this.basePath = basePath;
    }

    @Override
    public Resource getAvatar(int uid) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public void saveAvatar(int uid, Resource resource) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public boolean quickSave(int uid, String path, String name, String md5) throws IOException {
        return false;
    }

    @Override
    public void compressAndWriteOut(int uid, String path, Collection<String> names, ArchiveType type, OutputStream outputStream) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public void compress(int uid, String path, Collection<String> names, String dest, ArchiveType type) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public void extractArchive(int uid, String path, String name, String dest) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public boolean exist(int uid, String path) {
        return storeHandler.exist(StringUtils.appendPath(basePath, path));
    }

    @Override
    public Resource getResource(int uid, String path, String name) throws IOException {
        return storeHandler.getResource(StringUtils.appendPath(basePath, path, name));
    }

    @Override
    public String mkdirs(int uid, String path) throws IOException {
        storeHandler.mkdirs(StringUtils.appendPath(basePath, path));
        return null;
    }

    @Override
    public Resource getResourceByMd5(String md5) throws IOException {
        throw new UnsupportedOperationException("不支持的操作");
    }

    @Override
    public void copy(int uid, String source, String target, int targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException {
        if (uid != targetUid) {
            throw new UnsupportedOperationException("不支持跨用户网盘复制");
        }

        camHandler.copy(StringUtils.appendPath(basePath, source, sourceName), StringUtils.appendPath(basePath, target, targetName), overwrite);
    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        camHandler.copy(StringUtils.appendPath(basePath, source, name), StringUtils.appendPath(basePath, target, name), overwrite);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FileInfo>[] getUserFileList(int uid, String path) throws IOException {
        List<FileInfo> fileInfos = storeHandler.listFiles(StringUtils.appendPath(basePath, path));
        if (fileInfos == null) {
            return null;
        }
        List<FileInfo>[] res = new List[2];
        res[0] = fileInfos.stream().filter(BasicFileInfo::isDir).collect(Collectors.toList());
        res[1] = fileInfos.stream().filter(BasicFileInfo::isFile).collect(Collectors.toList());
        return res;
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(int uid, boolean reverse) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileInfo>[] getUserFileListByNodeId(int uid, String nodeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileInfo> search(int uid, String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void moveToSaveFile(int uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        if (!Files.exists(nativeFilePath) || Files.isDirectory(nativeFilePath)) {
            throw new IllegalArgumentException(nativeFilePath + " 不是文件或不存在");
        }
        try (InputStream is = Files.newInputStream(nativeFilePath)) {
            storeHandler.store(StringUtils.appendPath(basePath, path, fileInfo.getName()), Files.size(nativeFilePath), is);
        }

    }

    @Override
    public long saveFile(int uid, InputStream stream, String path, FileInfo fileInfo) throws IOException {
        return storeHandler.store(StringUtils.appendPath(basePath, path, fileInfo.getName()), fileInfo.getSize(), stream);
    }

    @Override
    public long saveFile(int uid, MultipartFile file, String requestPath, String md5) throws IOException {
        long size = file.getSize();
        storeHandler.store(StringUtils.appendPath(basePath, requestPath, file.getName()), size, file.getInputStream());
        return size;
    }

    @Override
    public void mkdir(int uid, String path, String name) throws IOException {
        storeHandler.mkdir(StringUtils.appendPath(basePath, path, name));
    }

    @Override
    public long deleteFile(int uid, String path, List<String> name) throws IOException {
        for (String n : name) {
            storeHandler.delete(StringUtils.appendPath(basePath, path, n));
        }
        return name.size();
    }

    @Override
    public void rename(int uid, String path, String name, String newName) throws IOException {
        storeHandler.rename(StringUtils.appendPath(basePath, path, name), newName);
    }
}
