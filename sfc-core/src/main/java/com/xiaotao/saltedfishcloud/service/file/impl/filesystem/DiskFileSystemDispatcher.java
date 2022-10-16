package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件系统匹配结果
 */
class FileSystemMatchResult {
    /**
     * 匹配到的文件系统
     */
    DiskFileSystem fileSystem;

    /**
     * 在对应的文件系统上解析后的对应路径（原始请求路径）
     */
    String resolvedPath;

    /**
     * 匹配到的挂载点，若没匹配到则为null
     */
    MountPoint mountPoint;

    public FileSystemMatchResult(DiskFileSystem fileSystem,MountPoint mountPoint, String resolvedPath) {
        this.fileSystem = fileSystem;
        this.mountPoint = mountPoint;
        this.resolvedPath = resolvedPath;
    }

    @Override
    public String toString() {
        return "FileSystemMatchResult{" +
                "fileSystem=" + fileSystem +
                ", resolvedPath='" + resolvedPath + '\'' +
                '}';
    }

    /**
     * 判断请求的路径是否为匹配的挂载点本身
     */
    public boolean isMountPath(String path) {
        if (mountPoint == null) {
            return false;
        }
        return StringUtils.isPathEqual(path, mountPoint.getPath());
    }
}

/**
 * 特殊的文件系统，文件系统指派器，根据请求的路径指派相对应的文件系统类型来执行相关请求，为实现第三方文件系统挂载点功能提供支持。
 */
@Data
@Slf4j
public class DiskFileSystemDispatcher implements DiskFileSystem {
    private final static String LOG_PREFIX = "[FileSystemDispatcher]";
    private DiskFileSystem mainFileSystem;
    private MountPointService mountPointService;
    private DiskFileSystemManager diskFileSystemManager;

    /**
     * 构造一个指派器
     * @param mainFileSystem        主文件系统
     * @param mountPointService     挂载点服务
     * @param diskFileSystemManager 文件系统管理器
     */
    public DiskFileSystemDispatcher(DiskFileSystem mainFileSystem, MountPointService mountPointService, DiskFileSystemManager diskFileSystemManager) {
        this.mainFileSystem = mainFileSystem;
        this.mountPointService = mountPointService;
        this.diskFileSystemManager = diskFileSystemManager;
    }

    @Override
    public Resource getThumbnail(int uid, String path, String name) throws IOException {
        FileSystemMatchResult fileSystemMatchResult = matchFileSystem(uid, path);
        return fileSystemMatchResult.fileSystem.getThumbnail(uid, fileSystemMatchResult.resolvedPath, name);
    }

    /**
     * 根据给定的uid和请求路径，匹配所处的挂载点路径
     * @param uid   用户id
     * @param path  请求路径
     * @return      若匹配到挂载点，则返回，否则为null
     */
    protected MountPoint matchMountPoint(int uid, String path) {
        Map<String, MountPoint> mountPointMap = mountPointService.findMountPointPathByUid(uid);
        String name = StringUtils.getURLLastName(path);
        if (name == null) {
            return null;
        }
        // 遍历所有挂载点，匹配所处路径前缀相同且名称相同的
        return mountPointMap.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .findAny()
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    /**
     * 根据请求的路径匹配对应的文件系统
     * @param uid   用户id
     * @param path  请求的路径
     * @return      匹配结果
     */
    protected FileSystemMatchResult matchFileSystem(int uid, String path) {
        MountPoint mountPoint = matchMountPoint(uid, path);
        if (mountPoint == null) {
            return new FileSystemMatchResult(mainFileSystem, mountPoint, path);
        } else {
            try {
                Map<String, Object> map = MapperHolder.parseJsonToMap(mountPoint.getParams());
                DiskFileSystem fileSystem = diskFileSystemManager.getFileSystem(mountPoint.getProtocol(), map);
                return new FileSystemMatchResult(fileSystem,mountPoint ,resolvePath(mountPoint.getPath(), path));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 将请求路径解析为目标挂载点的相对路径
     * @param mountPath     挂载点的挂载路径
     * @param requestPath   请求路径
     * @return              挂载点下的相对路径
     */
    protected String resolvePath(String mountPath, String requestPath) {
        if (requestPath.startsWith("/")) {
            return requestPath.substring(mountPath.length());
        } else {
            return StringUtils.appendPath("/", requestPath.substring(mountPath.length()));
        }
    }

    @Override
    public Resource getAvatar(int uid) throws IOException {
        return mainFileSystem.getAvatar(uid);
    }

    @Override
    public void saveAvatar(int uid, Resource resource) throws IOException {
        mainFileSystem.saveAvatar(uid, resource);
    }

    @Override
    public boolean quickSave(int uid, String path, String name, String md5) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        return matchResult.fileSystem.quickSave(uid, matchResult.resolvedPath, name, md5);
    }

    @Override
    public void compressAndWriteOut(int uid, String path, Collection<String> names, ArchiveType type, OutputStream outputStream) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        matchResult.fileSystem.compressAndWriteOut(uid, matchResult.resolvedPath, names, type, outputStream);
    }

    @Override
    public void compress(int uid, String path, Collection<String> names, String dest, ArchiveType type) throws IOException {
        FileSystemMatchResult sourceMatchResult = matchFileSystem(uid, path);
        FileSystemMatchResult targetMatchResult = matchFileSystem(uid, dest);
        if (!sourceMatchResult.fileSystem.equals(mainFileSystem) || !targetMatchResult.fileSystem.equals(mainFileSystem)) {
            throw new UnsupportedOperationException("暂不支持对挂载路径进行在线压缩到网盘操作");
        }
        sourceMatchResult.fileSystem.compress(uid, sourceMatchResult.resolvedPath, names, dest, type);
    }

    @Override
    public void extractArchive(int uid, String path, String name, String dest) throws IOException {
        FileSystemMatchResult sourceMatchResult = matchFileSystem(uid, path);
        FileSystemMatchResult targetMatchResult = matchFileSystem(uid, dest);
        if (!sourceMatchResult.fileSystem.equals(mainFileSystem) || !targetMatchResult.fileSystem.equals(mainFileSystem)) {
            throw new UnsupportedOperationException("暂不支持对挂载路径进行解压操作");
        }
        sourceMatchResult.fileSystem.extractArchive(uid, sourceMatchResult.resolvedPath, name, dest);
    }

    @Override
    public boolean exist(int uid, String path) {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        if (matchResult.mountPoint != null && (matchResult.resolvedPath.equals("/") || matchResult.resolvedPath.equals(""))) {
            return true;
        } else {
            return matchResult.fileSystem.exist(uid, matchResult.resolvedPath);
        }

    }

    @Override
    public Resource getResource(int uid, String path, String name) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        return matchResult.fileSystem.getResource(uid, matchResult.resolvedPath, name);
    }

    @Override
    public String mkdirs(int uid, String path) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        if (matchResult.isMountPath(path)) {
            throw new JsonException(FileSystemError.MOUNT_POINT_EXIST);
        }
        return matchResult.fileSystem.mkdirs(uid, matchResult.resolvedPath);
    }

    @Override
    public Resource getResourceByMd5(String md5) throws IOException {
        return mainFileSystem.getResourceByMd5(md5);
    }

    private void doCopy(int uid, String source, String target, int targetUid, String sourceName, String targetName, Boolean overwrite, int depth) throws IOException {
        log.debug("{}执行复制：{}/{} -> {}/{}", LOG_PREFIX, source, sourceName, target, targetName);
        if (depth >= 64) {
            throw new IOException("目录嵌套层数过大！（不能大于64）");
        }


        FileSystemMatchResult sourceMatchResult = matchFileSystem(uid, source);
        FileSystemMatchResult targetMatchResult = matchFileSystem(targetUid, target);
        if (Objects.equals(sourceMatchResult.fileSystem, mainFileSystem) && Objects.equals(targetMatchResult.fileSystem, mainFileSystem)) {
            log.debug("{}相同文件系统内copy: {}/{} -> {}/{}", LOG_PREFIX, source, sourceName, target, targetName);
            sourceMatchResult.fileSystem.copy(uid, source, target, targetUid, sourceName, targetName, overwrite);
            return;
        }

        log.debug("{}不同文件系统间copy: {}/{} -> {}/{}，文件系统：{} -> {}", LOG_PREFIX, source, sourceName, target, targetName, sourceMatchResult, targetMatchResult);

        Resource sourceResource = sourceMatchResult.fileSystem.getResource(uid, sourceMatchResult.resolvedPath, sourceName);
        if (sourceResource != null) {
            List<FileInfo>[] userFileList = sourceMatchResult.fileSystem.getUserFileList(uid, sourceMatchResult.resolvedPath);
            FileInfo fileInfo = Optional.ofNullable(userFileList[1])
                    .orElseThrow(() -> new IOException(source + " 下获取不到文件列表"))
                    .stream()
                    .filter(e -> e.getName().equals(sourceName))
                    .findAny()
                    .orElseThrow(() -> new IOException(source + "/" + sourceName + " 不存在"));

            targetMatchResult.fileSystem.saveFile(targetUid, sourceResource.getInputStream(), targetMatchResult.resolvedPath, fileInfo);
            return;
        }
        String resolvedSourcePath = StringUtils.appendPath(sourceMatchResult.resolvedPath, sourceName);
        String resolvedTargetPath = StringUtils.appendPath(targetMatchResult.resolvedPath, targetName);
        if(!targetMatchResult.fileSystem.exist(targetUid, resolvedTargetPath)) {
            targetMatchResult.fileSystem.mkdir(targetUid, targetMatchResult.resolvedPath, targetName);
        }
        List<FileInfo>[] sourceFileList = sourceMatchResult.fileSystem.getUserFileList(uid, resolvedSourcePath);
        List<FileInfo> fileList = sourceFileList[1];
        // 先复制文件
        if (fileList != null) {
            for (FileInfo fileInfo : fileList) {
                Resource resource = sourceMatchResult.fileSystem.getResource(uid, resolvedSourcePath, fileInfo.getName());
                try(InputStream inputStream = resource.getInputStream()) {
                    targetMatchResult.fileSystem.saveFile(targetUid, inputStream, resolvedTargetPath, fileInfo);
                }
            }
        }

        // 再复制目录
        List<FileInfo> dirList = sourceFileList[0];
        if (dirList != null) {

            List<FileInfo>[] targetList = targetMatchResult.fileSystem.getUserFileList(targetUid, resolvedTargetPath);
            List<FileInfo> targetFileList = targetList[0];
            List<FileInfo> targetDirList = targetList[1];
            Set<String> existDir;
            Set<String> existFile;
            if (targetDirList != null) {
                existDir = targetDirList.stream().map(BasicFileInfo::getName).collect(Collectors.toSet());
            } else {
                existDir = Collections.emptySet();
            }

            if (targetFileList != null) {
                existFile = targetFileList.stream().map(BasicFileInfo::getName).collect(Collectors.toSet());
            } else {
                existFile = Collections.emptySet();
            }

            for (FileInfo fileInfo : dirList) {
                // 跳过挂载点
                if (fileInfo.isMount()) {
                    continue;
                }
                if (!existDir.contains(fileInfo.getName())) {
                    if (existFile.contains(fileInfo.getName())) {
                        log.warn("{}复制目标路径已存在同名文件：{}/{}", LOG_PREFIX, resolvedTargetPath, fileInfo.getName());
                        continue;
                    }
                    targetMatchResult.fileSystem.mkdir(targetUid, resolvedTargetPath, fileInfo.getName());
                }
                doCopy(uid, StringUtils.appendPath(source, sourceName), StringUtils.appendPath(target, targetName), targetUid, fileInfo.getName(), fileInfo.getName(), overwrite, ++depth);
            }
        }
    }

    @Override
    public void copy(int uid, String source, String target, int targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException {
        doCopy(uid, source, target, targetUid, sourceName, targetName, overwrite, 0);
    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        FileSystemMatchResult sourceMatchResult = matchFileSystem(uid, source);
        FileSystemMatchResult targetMatchResult = matchFileSystem(uid, target);
        if (Objects.equals(sourceMatchResult.fileSystem, mainFileSystem) && Objects.equals(targetMatchResult.fileSystem, mainFileSystem)) {
            sourceMatchResult.fileSystem.move(uid, source, target, name, overwrite);
            return;
        }
        copy(uid, source, target, uid, name, name, true);
        deleteFile(uid, source, Collections.singletonList(name));
    }

    @Override
    public List<FileInfo>[] getUserFileList(int uid, String path) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        List<FileInfo>[] res = matchResult.fileSystem.getUserFileList(uid, matchResult.resolvedPath);

        // 若匹配到的文件系统不是主文件系统，则说明是挂载的文件系统，设置文件的mount属性为true
        if (!matchResult.fileSystem.equals(mainFileSystem)) {
            for (List<FileInfo> fileList : res) {
                for (FileInfo fileInfo : fileList) {
                    fileInfo.setMount(true);
                    fileInfo.setUid(uid);
                }
            }
        }
        return res;
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(int uid, boolean reverse) {
        return mainFileSystem.collectFiles(uid, reverse);
    }

    @Override
    public List<FileInfo>[] getUserFileListByNodeId(int uid, String nodeId) {
        return mainFileSystem.getUserFileListByNodeId(uid, nodeId);
    }

    @Override
    public List<FileInfo> search(int uid, String key) {
        return mainFileSystem.search(uid, key);
    }

    @Override
    public void moveToSaveFile(int uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        matchResult.fileSystem.moveToSaveFile(uid, nativeFilePath, matchResult.resolvedPath, fileInfo);
    }

    @Override
    public long saveFile(int uid, InputStream stream, String path, FileInfo fileInfo) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        return matchResult.fileSystem.saveFile(uid, stream, path, fileInfo);
    }

    @Override
    public long saveFile(int uid, MultipartFile file, String requestPath, String md5) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, requestPath);
        return matchResult.fileSystem.saveFile(uid, file, matchResult.resolvedPath, md5);
    }

    @Override
    public void mkdir(int uid, String path, String name) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        matchResult.fileSystem.mkdir(uid, matchResult.resolvedPath, name);
    }

    @Override
    public long deleteFile(int uid, String path, List<String> name) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        // 得到挂载完整路径 -> 挂载点 的map
        Map<String, MountPoint> mountPointMap = mountPointService.findMountPointPathByUid(uid);
        Set<Long> deleteMountId = new HashSet<>();
        for (String n : name) {
            String deletePath = StringUtils.appendPath(path, n);
            List<Long> ids = mountPointMap.entrySet()
                    .stream()
                    .filter(e -> e.getKey().startsWith(deletePath))
                    .map(e -> e.getValue().getId())
                    .collect(Collectors.toList());

            if (!ids.isEmpty()) {
                deleteMountId.addAll(ids);
            }
        }
        if (!deleteMountId.isEmpty()) {
            log.info("{}:删除的路径中存在以下挂载点需要删除：{}", LOG_PREFIX, deleteMountId);
            mountPointService.batchRemove(uid, deleteMountId);
        }

        return matchResult.fileSystem.deleteFile(uid, matchResult.resolvedPath, name);
    }

    @Override
    public void rename(int uid, String path, String name, String newName) throws IOException {
        String originPath = StringUtils.appendPath(path, name);
        FileSystemMatchResult matchResult = matchFileSystem(uid, originPath);
        if (matchResult.isMountPath(originPath)) {
            if(matchResult.fileSystem.exist(uid, StringUtils.appendPath(path, newName))) {
                throw new JsonException(FileSystemError.FILE_EXIST);
            }
            try {
                matchResult.mountPoint.setName(newName);
                mountPointService.saveMountPoint(matchResult.mountPoint);
            } catch (FileSystemParameterException e) {
                e.printStackTrace();
                throw new JsonException(e.getMessage());
            }
        } else {
            mainFileSystem.rename(uid, path, name, newName);
        }

    }
}
