package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.sfc.constant.error.CommonError;
import com.sfc.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.DiskFileSystemUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
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

    /**
     * 判断是否为一个启用了代理文件存储记录的挂载点
     */
    public boolean isProxyStoreRecordMountPoint() {
        return mountPoint != null && Boolean.TRUE.equals(mountPoint.getIsProxyStoreRecord());
    }
}

/**
 * 特殊的文件系统，文件系统指派器，根据请求的路径指派相对应的文件系统类型来执行相关请求，为实现第三方文件系统挂载点功能提供支持。
 */
@Slf4j
@Component
public class DiskFileSystemDispatcher implements DiskFileSystem {
    private final static String LOG_PREFIX = "[FileSystemDispatcher]";

    @Getter
    private DiskFileSystem mainFileSystem;

    @Autowired
    private MountPointService mountPointService;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private FileRecordService fileRecordService;

    @Autowired
    private NodeService nodeService;


    public void setMainFileSystem(DiskFileSystem mainFileSystem) {
        if (this.mainFileSystem != null) {
            throw new IllegalArgumentException("已经设置了主文件系统:" + mainFileSystem.getClass());
        }
        this.mainFileSystem = mainFileSystem;
    }

    @Override
    public List<FileInfo> getUserFileList(long uid, String path, @Nullable Collection<String> nameList) throws IOException {
        FileSystemMatchResult fileSystemMatchResult = matchFileSystem(uid, path);
        if (fileSystemMatchResult.isProxyStoreRecordMountPoint()) {
            return getMainFileSystem().getUserFileList(uid, path, nameList);
        }
        return fileSystemMatchResult.fileSystem.getUserFileList(uid, fileSystemMatchResult.resolvedPath, nameList);
    }

    @Override
    public Resource getThumbnail(long uid, String path, String name) throws IOException {
        FileSystemMatchResult fileSystemMatchResult = matchFileSystem(uid, path);
        return fileSystemMatchResult.fileSystem.getThumbnail(uid, fileSystemMatchResult.resolvedPath, name);
    }

    /**
     * 根据给定的uid和请求路径，匹配所处的挂载点路径
     * @param uid   用户id
     * @param path  请求路径
     * @return      若匹配到挂载点，则返回，否则为null
     */
    protected MountPoint matchMountPoint(long uid, String path) {
        Map<String, MountPoint> mountPointMap = mountPointService.findMountPointPathByUid(uid);
        String name = StringUtils.getURLLastName(path);
        if (name == null) {
            return null;
        }
        // 路径后面添加一个/，防止出现因为部分前缀匹配导致的误判，如：当访问 /挂载点12 时候,若存在 /挂载点1，则会误匹配到/挂载点1
        String path2 = path + "/";
        // 遍历所有挂载点，匹配所处路径前缀相同且名称相同的
        return mountPointMap.entrySet().stream()
                .filter(e -> path2.startsWith(e.getKey()))
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
    protected FileSystemMatchResult matchFileSystem(long uid, String path) {
        MountPoint mountPoint = matchMountPoint(uid, path);
        if (mountPoint == null) {
            return new FileSystemMatchResult(mainFileSystem, null, path);
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
        int prefixLength;
        if (mountPath.endsWith("/")) {
            prefixLength = mountPath.length() - 1;
        } else {
            prefixLength = mountPath.length();
        }
        if (requestPath.startsWith("/")) {
            return requestPath.substring(prefixLength);
        } else {
            return StringUtils.appendPath("/", requestPath.substring(prefixLength));
        }
    }

    @Override
    public Resource getAvatar(long uid) throws IOException {
        return mainFileSystem.getAvatar(uid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAvatar(long uid, Resource resource) throws IOException {
        mainFileSystem.saveAvatar(uid, resource);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quickSave(long uid, String path, String name, String md5) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        boolean isSuccess = matchResult.fileSystem.quickSave(uid, matchResult.resolvedPath, name, md5);
        if (matchResult.isProxyStoreRecordMountPoint() && isSuccess) {
            Optional.ofNullable(fileRecordService.getFileInfoByMd5(md5, 1))
                    .orElseThrow(() -> new JsonException(
                            CommonError.MOUNT_POINT_FILE_RECORD_PROXY_ERROR.getStatus(),
                            CommonError.MOUNT_POINT_FILE_RECORD_PROXY_ERROR.getCode(),
                            CommonError.MOUNT_POINT_FILE_RECORD_PROXY_ERROR.getMessage() + ": 找不到节点id。位置: " + path + "/" + name + " 文件md5: " + md5)
                    );
        }
        return isSuccess;
    }

    @Override
    public boolean exist(long uid, String path) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        if (matchResult.mountPoint != null && (matchResult.resolvedPath.equals("/") || matchResult.resolvedPath.equals(""))) {
            return true;
        } else {
            if (matchResult.isProxyStoreRecordMountPoint()) {
                return getMainFileSystem().exist(uid, path);
            } else {
                return matchResult.fileSystem.exist(uid, matchResult.resolvedPath);
            }
        }

    }

    @Override
    public Resource getResource(long uid, String path, String name) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        return matchResult.fileSystem.getResource(uid, matchResult.resolvedPath, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdirs(long uid, String path) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        if (matchResult.isMountPath(path)) {
            throw new JsonException(FileSystemError.MOUNT_POINT_EXIST);
        }
        String mkdirsResult = matchResult.fileSystem.mkdirs(uid, matchResult.resolvedPath);
        if (matchResult.isProxyStoreRecordMountPoint()) {
            fileRecordService.mkdirs(uid, path, true);
        }
        return mkdirsResult;
    }

    @Override
    public Resource getResourceByMd5(String md5) throws IOException {
        Resource mainResource = getMainFileSystem().getResourceByMd5(md5);
        if(mainResource != null) {
            return mainResource;
        }
        FileInfo fileInfo;
        List<FileInfo> files = fileRecordService.getFileInfoByMd5(md5, 1);
        if (files.size() == 0) {
            throw new NoSuchFileException("文件不存在: " + md5);
        }
        fileInfo = files.get(0);
        String path = nodeService.getPathByNode(fileInfo.getUid(), fileInfo.getNode());
        fileInfo.setPath(path + "/" + fileInfo.getName());
        Resource resource = getResource(fileInfo.getUid(), path, fileInfo.getName());
        return ResourceUtils.bindFileInfo(resource, fileInfo);
    }

    private void doCopy(long uid, String sourceDir, String targetDir, long targetUid, String sourceName, String targetName, Boolean overwrite, int depth) throws IOException {
        log.debug("{}执行复制：{}/{} -> {}/{}", LOG_PREFIX, sourceDir, sourceName, targetDir, targetName);
        if (depth >= 64) {
            throw new IOException("目录嵌套层数过大！（不能大于64）");
        }


        FileSystemMatchResult sourceMatchResult = matchFileSystem(uid, sourceDir);
        FileSystemMatchResult targetMatchResult = matchFileSystem(targetUid, targetDir);
        if (Objects.equals(sourceMatchResult.fileSystem, mainFileSystem) && Objects.equals(targetMatchResult.fileSystem, mainFileSystem)) {
            log.debug("{}相同文件系统内copy: {}/{} -> {}/{}", LOG_PREFIX, sourceDir, sourceName, targetDir, targetName);
            sourceMatchResult.fileSystem.copy(uid, sourceDir, targetDir, targetUid, sourceName, targetName, overwrite);
            return;
        }

        log.debug("{}不同文件系统间copy: {}/{} -> {}/{}，文件系统：{} -> {}", LOG_PREFIX, sourceDir, sourceName, targetDir, targetName, sourceMatchResult, targetMatchResult);

        // 如果能拿到Resource 说明被复制对象是文件，直接复制文件即可
        Resource sourceResource = sourceMatchResult.fileSystem.getResource(uid, sourceMatchResult.resolvedPath, sourceName);
        if (sourceResource != null) {
            List<FileInfo>[] userFileList = sourceMatchResult.fileSystem.getUserFileList(uid, sourceMatchResult.resolvedPath);
            FileInfo fileInfo = Optional.ofNullable(userFileList[1])
                    .orElseThrow(() -> new IOException(sourceDir + " 下获取不到文件列表"))
                    .stream()
                    .filter(e -> e.getName().equals(sourceName))
                    .findAny()
                    .orElseThrow(() -> new IOException(sourceDir + "/" + sourceName + " 不存在"));
            fileInfo.setStreamSource(sourceResource);
            FileInfo newFile = FileInfo.createFrom(fileInfo, false);
            newFile.setUid(targetUid);
            newFile.setStreamSource(sourceResource);
            targetMatchResult.fileSystem.saveFile(newFile, targetMatchResult.resolvedPath);
            if (targetMatchResult.isProxyStoreRecordMountPoint()) {
                newFile.setNode(nodeService.getNodeIdByPath(targetUid, targetDir));
                newFile.setIsMount(true);
                fileRecordService.saveRecord(newFile, targetDir);
            }
            return;
        }

        // 被复制对象是目录
        String resolvedSourcePath = StringUtils.appendPath(sourceMatchResult.resolvedPath, sourceName);
        String resolvedTargetPath = StringUtils.appendPath(targetMatchResult.resolvedPath, targetName);
        if (targetMatchResult.isProxyStoreRecordMountPoint()) {
            if (!fileRecordService.exist(targetUid, targetDir, targetName)) {
                fileRecordService.mkdirs(targetUid, StringUtils.appendPath(targetDir, targetName), true);
                targetMatchResult.fileSystem.mkdir(targetUid, targetMatchResult.resolvedPath, targetName);
            }
        } else if(!targetMatchResult.fileSystem.exist(targetUid, resolvedTargetPath)) {
            targetMatchResult.fileSystem.mkdir(targetUid, targetMatchResult.resolvedPath, targetName);
        }

        List<FileInfo>[] sourceFileList = sourceMatchResult.fileSystem.getUserFileList(uid, resolvedSourcePath);
        List<FileInfo> fileList = sourceFileList[1];
        // 先复制文件
        if (fileList != null) {
            String fileNodeId = targetMatchResult.isProxyStoreRecordMountPoint() ? nodeService.getNodeIdByPath(targetUid, StringUtils.appendPath(targetDir, targetName)) : null;
            for (FileInfo fileInfo : fileList) {
                Resource resource = sourceMatchResult.fileSystem.getResource(uid, resolvedSourcePath, fileInfo.getName());
                fileInfo.setStreamSource(resource);
                FileInfo newFile = FileInfo.createFrom(fileInfo, false);
                newFile.setUid(targetUid);
                targetMatchResult.fileSystem.saveFile(newFile, resolvedTargetPath);
                if (targetMatchResult.isProxyStoreRecordMountPoint()) {
                    newFile.setNode(fileNodeId);
                    newFile.setIsMount(true);
                    fileRecordService.saveRecord(newFile, targetDir);
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
                existDir = targetDirList.stream().map(FileInfo::getName).collect(Collectors.toSet());
            } else {
                existDir = Collections.emptySet();
            }

            if (targetFileList != null) {
                existFile = targetFileList.stream().map(FileInfo::getName).collect(Collectors.toSet());
            } else {
                existFile = Collections.emptySet();
            }

            int nextDepth = depth + 1;
            for (FileInfo fileInfo : dirList) {
                // 跳过挂载点
                if (fileInfo.getMountId() != null) {
                    continue;
                }
                if (!existDir.contains(fileInfo.getName())) {
                    if (existFile.contains(fileInfo.getName())) {
                        log.warn("{}复制目标路径已存在同名文件：{}/{}", LOG_PREFIX, resolvedTargetPath, fileInfo.getName());
                        continue;
                    }
                    targetMatchResult.fileSystem.mkdir(targetUid, resolvedTargetPath, fileInfo.getName());
                    if (targetMatchResult.isProxyStoreRecordMountPoint()) {
                        fileRecordService.mkdirs(targetUid, StringUtils.appendPath(targetDir, fileInfo.getName()), true);
                    }
                }
                doCopy(uid, StringUtils.appendPath(sourceDir, sourceName), StringUtils.appendPath(targetDir, targetName), targetUid, fileInfo.getName(), fileInfo.getName(), overwrite, nextDepth);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copy(long uid, String source, String target, long targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException {
        doCopy(uid, source, target, targetUid, sourceName, targetName, overwrite, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(long uid, String source, String target, String name, boolean overwrite) throws IOException {
        String sourcePath = StringUtils.appendPath(source, name);
        String targetPath = StringUtils.appendPath(target, name);
        FileSystemMatchResult targetMatchResult = matchFileSystem(uid, target);
        FileSystemMatchResult sourceMatchResult = matchFileSystem(uid, source);
        FileSystemMatchResult sourceFullMatchResult = matchFileSystem(uid, sourcePath);
        FileSystemMatchResult targetFullMatchResult = matchFileSystem(uid, targetPath);

        List<MountPoint> mountPoints = null;
        Resource resource = sourceMatchResult.fileSystem.getResource(uid, sourceMatchResult.resolvedPath, name);
        if (resource == null) {
            mountPoints = mountPointService.listByPath(uid, sourcePath);

            // 如果目标位置是挂载点位置，需要判断源目标中是否包含挂载点，如果有挂载点是不允许的，防止挂载点内出现嵌套，不好处理。
            if (targetMatchResult.mountPoint != null || targetFullMatchResult.isMountPath(targetPath)) {
                if (mountPoints != null && !mountPoints.isEmpty()) {
                    throw new JsonException("目录包含挂载点，不能移动到其他挂载点下");
                }
                if (sourceFullMatchResult.isMountPath(sourcePath)) {
                    throw new JsonException("挂载点不允许移动到其他挂载点下");
                }
            }

            // 如果移动的是挂载点本身，那么只需要修改挂载点的nid就好了
            if (sourceFullMatchResult.isMountPath(sourcePath)) {
                String nodeId = nodeService.getNodeIdByPath(uid, target);
                MountPoint mountPoint = sourceFullMatchResult.mountPoint;
                mountPoint.setNid(nodeId);
                try {
                    mountPointService.saveMountPoint(mountPoint);
                    return;
                } catch (FileSystemParameterException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        if (Objects.equals(sourceMatchResult.fileSystem, targetMatchResult.fileSystem)) {
            sourceMatchResult.fileSystem.move(uid, source, target, name, overwrite);
        } else {
            copy(uid, source, target, uid, name, name, true);
            deleteFile(uid, source, Collections.singletonList(name));
        }

        // 挂载点被移动，需要清理掉缓存
        if (mountPoints != null && !mountPoints.isEmpty()) {
            mountPointService.clearCache(uid);
        }
    }

    @Override
    public List<FileInfo>[] getUserFileList(long uid, String path) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        List<FileInfo>[] res;
        if (matchResult.isProxyStoreRecordMountPoint()) {
            res = mainFileSystem.getUserFileList(uid, path);
        } else {
            res = matchResult.fileSystem.getUserFileList(uid, matchResult.resolvedPath);
        }

        // 若匹配到的文件系统不是主文件系统，则说明是挂载的文件系统，设置文件的mount属性为true
        if (!matchResult.fileSystem.equals(mainFileSystem)) {
            for (List<FileInfo> fileList : res) {
                for (FileInfo fileInfo : fileList) {
                    fileInfo.setIsMount(true);
                    fileInfo.setUid(uid);
                }
            }
        }
        return res;
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(long uid, boolean reverse) {
        return mainFileSystem.collectFiles(uid, reverse);
    }

    @Override
    public List<FileInfo>[] getUserFileListByNodeId(long uid, String nodeId) {
        return mainFileSystem.getUserFileListByNodeId(uid, nodeId);
    }

    @Override
    public List<FileInfo> search(long uid, String key) {
        return mainFileSystem.search(uid, key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveToSaveFile(long uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        matchResult.fileSystem.moveToSaveFile(uid, nativeFilePath, matchResult.resolvedPath, fileInfo);
        if (matchResult.isProxyStoreRecordMountPoint()) {
            fileInfo.setUid(uid);
            fileRecordService.saveRecord(fileInfo, path);
        }
    }

    @Override
    public long saveFile(FileInfo file, String requestPath) throws IOException {
        if(!FileNameValidator.valid(file.getName())) {
            throw new IllegalArgumentException("非法文件名，不可包含/\\<>?|:换行符，回车符或文件名为..");
        }
        FileSystemMatchResult matchResult = matchFileSystem(file.getUid(), requestPath);
        long res = matchResult.fileSystem.saveFile(file, matchResult.resolvedPath);
        if (matchResult.isProxyStoreRecordMountPoint()) {
            fileRecordService.saveRecord(file, requestPath);
        }
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mkdir(long uid, String path, String name) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        matchResult.fileSystem.mkdir(uid, matchResult.resolvedPath, name);
        if (matchResult.isProxyStoreRecordMountPoint()) {
            fileRecordService.mkdirs(uid, StringUtils.appendPath(path, name), true);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long deleteFile(long uid, String path, List<String> name) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        // 得到挂载完整路径 -> 挂载点 的map
        Map<String, MountPoint> mountPointMap = mountPointService.findMountPointPathByUid(uid);

        // 筛选出删除的文件中包含的挂载点
        Map<Long, MountPoint> needRemoveMountPointMap = name.stream()
                .flatMap(n -> mountPointMap.entrySet()
                        .stream()
                        .filter(e -> e.getKey().startsWith(StringUtils.appendPath(path, n)))
                        .map(Map.Entry::getValue)
                )
                .collect(Collectors.toMap(
                        MountPoint::getId,
                        Function.identity(),
                        (oldVal, newVal) -> oldVal
                ));
        if (!needRemoveMountPointMap.isEmpty()) {
            log.info("{}:删除的路径中存在以下挂载点需要删除：{}", LOG_PREFIX, needRemoveMountPointMap.keySet());
            mountPointService.batchRemove(uid, needRemoveMountPointMap.keySet());
        }
        if (matchResult.isProxyStoreRecordMountPoint()) {
            fileRecordService.deleteRecords(uid, path, name);
        }
        return matchResult.fileSystem.deleteFile(uid, matchResult.resolvedPath, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(long uid, String path, String name, String newName) throws IOException {
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
            matchResult = matchFileSystem(uid, path);
            if (matchResult.isProxyStoreRecordMountPoint()) {
                fileRecordService.rename(uid, path, name, newName);
                matchResult.fileSystem.rename(uid, matchResult.resolvedPath, name, newName);
            } else {
                matchResult.fileSystem.rename(uid, matchResult.resolvedPath, name, newName);
            }
        }


    }

    @Override
    public String toString() {
        if (mainFileSystem == null) {
            log.error("{}找不到主文件系统！请检查是否正确配置sys.store.type", LOG_PREFIX);
            log.error("{}Could not find main filesystem! Please check whether sys.store.type is configured correctly", LOG_PREFIX);
            throw new IllegalArgumentException("!!! Could not find main filesystem! Please check whether sys.store.type is configured correctly !!!");
        } else {
            return mainFileSystem.getClass().getSimpleName() + " - Proxy by DiskFileSystemDispatcher";
        }
    }

    @Override
    public List<FileSystemStatus> getStatus() {
        return mainFileSystem.getStatus();
    }
}
