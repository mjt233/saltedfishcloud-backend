package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.event.cm.DirCopyEvent;
import com.xiaotao.saltedfishcloud.event.cm.DirMoveEvent;
import com.xiaotao.saltedfishcloud.event.cm.FileCopyEvent;
import com.xiaotao.saltedfishcloud.event.cm.FileMoveEvent;
import com.xiaotao.saltedfishcloud.event.dir.MkdirEvent;
import com.xiaotao.saltedfishcloud.event.file.FileDeleteEvent;
import com.xiaotao.saltedfishcloud.event.file.FileStoreEvent;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressCallback;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import jakarta.persistence.EntityManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
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
    @Lazy
    private MountPointService mountPointService;

    @Autowired
    @Lazy
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private FileRecordService fileRecordService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;


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
    private MountPoint matchMountPoint(long uid, String path) {
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
    private FileSystemMatchResult matchFileSystem(long uid, String path) {
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
    private String resolvePath(String mountPath, String requestPath) {
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

        if (isSuccess) {
            this.publishFileStoreEvent(matchResult.fileSystem, uid, path, name, StringUtils.appendPath(path, name), null);
        }
        return isSuccess;
    }

    @Override
    public boolean exist(long uid, String path) throws IOException {
        FileSystemMatchResult matchResult = matchFileSystem(uid, path);
        if (matchResult.mountPoint != null && (matchResult.resolvedPath.equals("/") || matchResult.resolvedPath.isEmpty())) {
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
        String dirNodeId = matchResult.fileSystem.mkdirs(uid, matchResult.resolvedPath);
        if (matchResult.isProxyStoreRecordMountPoint()) {
            fileRecordService.mkdirs(uid, path, true);
        }
        eventPublisher.publishEvent(new MkdirEvent(this, uid, path, dirNodeId));
        return dirNodeId;
    }

    @Override
    public Resource getResourceByMd5(String md5) throws IOException {
        Resource mainResource = getMainFileSystem().getResourceByMd5(md5);
        if(mainResource != null) {
            return mainResource;
        }
        FileInfo fileInfo;
        List<FileInfo> files = fileRecordService.getFileInfoByMd5(md5, 1);
        if (files.isEmpty()) {
            return null;
        }
        fileInfo = files.get(0);
        String path = fileRecordService.getPathByNodeId(fileInfo.getUid(), fileInfo.getNode())
                .orElseThrow(() -> new JsonException(FileSystemError.NODE_NOT_FOUND));
        fileInfo.setPath(path + "/" + fileInfo.getName());
        Resource resource = getResource(fileInfo.getUid(), path, fileInfo.getName());
        return ResourceUtils.bindFileInfo(resource, fileInfo);
    }

    /**
     * 发布文件保存事件
     * @param diskFileSystem    最终保存到的目标文件系统
     * @param uid   用户id
     * @param path  文件在目标文件系统上的所在目录的路径
     * @param name  文件名
     * @param fullPath  在主文件系统中的完整路径
     */
    private void publishFileStoreEvent(DiskFileSystem diskFileSystem, Long uid, String path, String name, String fullPath, FileInfo fileInfo) {
        eventPublisher.publishEvent(new FileStoreEvent(this, uid, fullPath, () -> {
            try {
                if (fileInfo != null) {
                    return fileInfo;
                }
                List<FileInfo> r = diskFileSystem.getUserFileList(uid, path, Collections.singletonList(name));
                if (r == null || r.isEmpty()) {
                    log.warn("{} 文件保存事件中未查询到文件信息 uid: {} fullPath: {}", LOG_PREFIX, uid, fullPath);
                    return null;
                }
                FileInfo f = r.get(0);
                f.setPath(fullPath);
                return f;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private void publishFileCopyOrMoveEvent(long sourceUid, String sourceDir, String targetDir, long targetUid, String sourceName, String targetName, boolean isMove) throws IOException {
        List<FileInfo> r = this.getMainFileSystem().getUserFileList(targetUid, targetDir, Collections.singletonList(targetName));
        if (r == null || r.isEmpty()) {
            log.warn("{} 未查询到文件信息，没能发布文件复制事件 uid: {} fullPath: {}", LOG_PREFIX, targetUid, StringUtils.appendPath(targetDir, targetName));
        } else {
            if(r.get(0).isFile()) {
                if (isMove) {
                    eventPublisher.publishEvent(new FileMoveEvent(this, sourceUid, StringUtils.appendPath(sourceDir, sourceName), targetUid, StringUtils.appendPath(targetDir, targetName)));
                } else {
                    eventPublisher.publishEvent(new FileCopyEvent(this, sourceUid, StringUtils.appendPath(sourceDir, sourceName), targetUid, StringUtils.appendPath(targetDir, targetName)));
                }
            } else {
                if (isMove) {
                    eventPublisher.publishEvent(new DirMoveEvent(this, sourceUid, StringUtils.appendPath(sourceDir, sourceName), targetUid, StringUtils.appendPath(targetDir, targetName)));
                } else {
                    eventPublisher.publishEvent(new DirCopyEvent(this, sourceUid, StringUtils.appendPath(sourceDir, sourceName), targetUid, StringUtils.appendPath(targetDir, targetName)));
                }
            }
        }
    }

    /**
     * 使用 SimpleFileTransferParam 参数批量复制文件，支持进度回调和中断
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copy(SimpleFileTransferParam param, CopyProgressCallback callback) throws IOException {
        doCopyInternal(param, callback, 0);
    }

    /**
     * 核心复制方法，支持进度回调（可为null）
     */
    private void doCopyInternal(SimpleFileTransferParam param, CopyProgressCallback callback, int depth) throws IOException {
        if (depth > 32) {
            throw new JsonException(FileSystemError.DIR_TOO_DEPTH, "目录深度超过32");
        }
        Long sourceUid = param.getSourceUid();
        String sourcePath = param.getSourcePath();
        Long targetUid = param.getTargetUid();
        String targetPath = param.getTargetPath();
        Boolean overwrite = param.getIsOverwrite();
        if (sourceUid == null || targetUid == null || sourcePath == null || targetPath == null) {
            throw new IllegalArgumentException("sourceUid, targetUid, sourcePath, targetPath 不能为 null");
        }

        // 相同内的复制，直接调用文件系统自己的copy方法
        log.debug("{}执行复制：{} -> {}", LOG_PREFIX, sourcePath, targetPath);
        FileSystemMatchResult sourceMatchResult = matchFileSystem(sourceUid, sourcePath);
        FileSystemMatchResult targetMatchResult = matchFileSystem(targetUid, targetPath);
        if (Objects.equals(sourceMatchResult.fileSystem, targetMatchResult.fileSystem)) {
            log.debug("{}相同文件系统内copy: {} -> {}", LOG_PREFIX, sourcePath, targetPath);
            if (callback != null && callback.shouldInterrupt()) {
                log.debug("{} 复制操作被中断", LOG_PREFIX);
                return;
            }
            sourceMatchResult.fileSystem.copy(SimpleFileTransferParam.builder()
                            .sourceUid(sourceUid)
                            .sourcePath(sourceMatchResult.resolvedPath)
                            .files(param.getFiles())
                            .targetUid(targetUid)
                            .targetPath(targetMatchResult.resolvedPath)
                            .isOverwrite(overwrite)
                    .build(), callback);
            return;
        }

        // 不同的文件系统间复制，采用普通的读取源文件保存到目标位置的方式
        log.debug("{}不同文件系统间copy: {} -> {}，文件系统：{} -> {}", LOG_PREFIX, sourcePath, sourcePath, sourceMatchResult, targetMatchResult);
        org.springframework.data.util.Lazy<String> nodeId = org.springframework.data.util.Lazy
                .of(() -> fileRecordService.getNodeIdByPath(targetUid, targetPath)
                        .orElseThrow(() -> new JsonException(404, "路径" + targetPath + "节点信息丢失"))
                );
        List<FileInfo> sourceFileList = ObjectUtils.cloneListElement(
                this.getUserFileList(sourceUid, sourcePath, param.getFiles()),
                FileInfo::new
        );
        List<String> sourceNames =  sourceFileList.stream().map(FileInfo::getName).toList();
        Map<String, FileInfo> targetExistFileMap = this.getUserFileList(targetUid, targetPath, sourceNames)
                .stream()
                .collect(Collectors.toMap(FileInfo::getName, f -> ObjectUtils.clone(f, FileInfo::new)));

        for (FileInfo sourceFile : sourceFileList) {
            if (callback != null && callback.shouldInterrupt()) {
                log.debug("{} 复制操作被中断", LOG_PREFIX);
                return;
            }
            // 将文件信息更新为目标位置的信息
            sourceFile.setUid(targetUid);
            sourceFile.setPath(targetMatchResult.resolvedPath);
            if (targetMatchResult.isProxyStoreRecordMountPoint()) {
                sourceFile.setNode(nodeId.get());
                sourceFile.setIsMount(true);
            }

            // 检查目标与源文件是否同为文件或文件夹
            FileInfo existFile = targetExistFileMap.get(sourceFile.getName());
            if (existFile != null && existFile.isDir() != sourceFile.isDir()) {
                throw new JsonException(sourceFile.isFile() ? FileSystemError.NOT_ALLOW_FILE_OVERWRITE_DIR : FileSystemError.NOT_ALLOW_DIR_OVERWRITE_FILE);
            }

            FileTransferItem transferRecord = FileTransferItem.builder()
                    .from(StringUtils.appendPath(sourcePath, sourceFile.getName()))
                    .to(StringUtils.appendPath(targetPath, sourceFile.getName()))
                    .fileInfo(sourceFile)
                    .total(sourceFile.isDir() ? 0 : sourceFile.getSize())
                    .build();
            if (sourceFile.isFile()) {
                if (callback != null) {
                    callback.onFileStart(transferRecord);
                }
                // 如果未开启覆盖，存在同名文件则应该跳过
                if (!Boolean.TRUE.equals(param.getIsOverwrite()) && existFile != null) {
                    transferRecord.setIsSkip(true);
                    if (callback != null) {
                        callback.onFileComplete(transferRecord);
                    }
                    continue;
                }
                // 文件直接复制
                if (callback != null && callback.shouldInterrupt()) {
                    log.debug("{} 复制操作被中断", LOG_PREFIX);
                    return;
                }
                Resource resource = sourceMatchResult.fileSystem.getResource(sourceUid, sourceMatchResult.resolvedPath, sourceFile.getName());
                sourceFile.setStreamSource(resource);
                sourceFile.setId(null);
                targetMatchResult.fileSystem.saveFile(sourceFile, targetMatchResult.resolvedPath);
                if (callback != null) {
                    callback.onFileComplete(transferRecord);
                }
                if (targetMatchResult.isProxyStoreRecordMountPoint()) {
                    fileRecordService.saveRecord(sourceFile, param.getTargetPath());
                }
            } else {
                String nextSourcePath = StringUtils.appendPath(sourcePath, sourceFile.getName());
                String nextTargetPath = StringUtils.appendPath(targetPath, sourceFile.getName());
                if (callback != null) {
                    callback.onDirStart(nextTargetPath);
                }
                // 目录则先创建目录，然后递归处理
                targetMatchResult.fileSystem.mkdir(targetUid, targetMatchResult.resolvedPath, sourceFile.getName());
                doCopyInternal(SimpleFileTransferParam.builder()
                        .sourceUid(sourceUid)
                        .sourcePath(nextSourcePath)
                        .targetUid(targetUid)
                        .targetPath(nextTargetPath)
                        .build(), callback, depth + 1);
                if (callback != null) {
                    callback.onDirComplete(nextTargetPath);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(long uid, String source, String target, String name, boolean overwrite) throws IOException {
        String fullSourcePath = StringUtils.appendPath(source, name);
        String fullTargetPath = StringUtils.appendPath(target, name);
        FileSystemMatchResult targetMatchResult = matchFileSystem(uid, target);
        FileSystemMatchResult sourceMatchResult = matchFileSystem(uid, source);
        FileSystemMatchResult sourceFullMatchResult = matchFileSystem(uid, fullSourcePath);
        FileSystemMatchResult targetFullMatchResult = matchFileSystem(uid, fullTargetPath);

        List<MountPoint> mountPoints = null;
        Resource resource = sourceMatchResult.fileSystem.getResource(uid, sourceMatchResult.resolvedPath, name);
        if (resource == null) {
            mountPoints = mountPointService.listByPath(uid, fullSourcePath);

            // 如果目标位置是挂载点位置，需要判断源目标中是否包含挂载点，如果有挂载点是不允许的，防止挂载点内出现嵌套，不好处理。
            if (targetMatchResult.mountPoint != null || targetFullMatchResult.isMountPath(fullTargetPath)) {
                if (mountPoints != null && !mountPoints.isEmpty()) {
                    throw new JsonException("目录包含挂载点，不能移动到其他挂载点下");
                }
                if (sourceFullMatchResult.isMountPath(fullSourcePath)) {
                    throw new JsonException("挂载点不允许移动到其他挂载点下");
                }
            }

            // 如果移动的是挂载点本身，那么只需要修改挂载点的nid就好了
            if (sourceFullMatchResult.isMountPath(fullSourcePath)) {
                String nodeId = fileRecordService.getNodeIdByPath(uid, target)
                        .orElseThrow(() -> new JsonException(FileSystemError.NODE_NOT_FOUND));
                MountPoint mountPoint = sourceFullMatchResult.mountPoint;
                mountPoint.setNid(nodeId);
                try {
                    mountPointService.saveMountPoint(mountPoint);
                    this.publishFileCopyOrMoveEvent(uid, source, target, uid, name, name, true);
                    return;
                } catch (FileSystemParameterException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        if (Objects.equals(sourceMatchResult.fileSystem, targetMatchResult.fileSystem)) {
            // 同文件系统，直接移动
            sourceMatchResult.fileSystem.move(uid, sourceMatchResult.resolvedPath, targetMatchResult.resolvedPath, name, overwrite);
        } else {
            // 跨文件系统，采用复制+删除分步操作
            doCopyInternal(SimpleFileTransferParam.builder()
                    .sourceUid(uid)
                    .sourcePath(source)
                    .files(List.of(name))
                    .targetUid(uid)
                    .targetPath(target)
                    .build(), null, 0);
            doDeleteFile(uid, source, Collections.singletonList(name));
        }

        // 挂载点被移动，需要清理掉缓存
        if (mountPoints != null && !mountPoints.isEmpty()) {
            mountPointService.clearCache(uid);
        }
        this.publishFileCopyOrMoveEvent(uid, source, target, uid, name, name, true);
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
        this.publishFileStoreEvent(matchResult.fileSystem, uid, matchResult.resolvedPath, fileInfo.getName(), StringUtils.appendPath(path, fileInfo.getName()), null);
    }

    @Override
    public void saveFileByStream(FileInfo file, String savePath, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        if(!FileNameValidator.valid(file.getName())) {
            throw new IllegalArgumentException("非法文件名，不可包含/\\<>?|:换行符，回车符或文件名为..");
        }
        FileSystemMatchResult matchResult = matchFileSystem(file.getUid(), savePath);
        matchResult.fileSystem.saveFileByStream(file, matchResult.resolvedPath, streamConsumer);
        if (matchResult.isProxyStoreRecordMountPoint()) {
            fileRecordService.saveRecord(file, savePath);
        }
        this.publishFileStoreEvent(matchResult.fileSystem, file.getUid(), savePath, file.getName(), StringUtils.appendPath(savePath, file.getName()), file);
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

    private long doDeleteFile(long uid, String path, List<String> name) throws IOException {
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
    public long deleteFile(long uid, String path, List<String> name) throws IOException {
        long res = this.doDeleteFile(uid, path, name);
        eventPublisher.publishEvent(new FileDeleteEvent(this, uid, path, name));
        return res;
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
                log.error("{}重命名文件时发生错误", LOG_PREFIX, e);
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
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        for (String name : names) {
            String filePath = StringUtils.appendPath(path, name);
            FileSystemMatchResult r = matchFileSystem(uid, filePath);
            r.fileSystem.updateTime(uid, PathUtils.getParentPath(r.resolvedPath), names, attribute);
            if (r.isProxyStoreRecordMountPoint()) {
                FileInfo fileInfo = fileRecordService.getFileInfo(uid, path, name);
                if (fileInfo == null) {
                    log.warn("{}文件记录不存在，忽略日期修改操作：{}", LOG_PREFIX, filePath);
                    continue;
                }
                if (attribute.apply(fileInfo)) {
                    fileRecordService.save(fileInfo);
                }
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
