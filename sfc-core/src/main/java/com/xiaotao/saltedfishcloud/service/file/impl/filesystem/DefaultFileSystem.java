package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.event.cm.FileMoveEvent;
import com.xiaotao.saltedfishcloud.event.dir.MkdirEvent;
import com.xiaotao.saltedfishcloud.event.file.FileDeleteEvent;
import com.xiaotao.saltedfishcloud.event.file.FileStoreEvent;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.model.progress.event.UpdateFileRecordCompleteEvent;
import com.xiaotao.saltedfishcloud.model.progress.event.UpdateFileRecordStartEvent;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StreamUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一的文件系统编排入口，负责挂载点路由、主文件系统分发与事件编排。
 */
@Slf4j
@Component
public class DefaultFileSystem implements DiskFileSystem, FeatureProvider {
    private static final String LOG_PREFIX = "[FileSystem]";

    /**
     * Spring事件发布器。
     */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 挂载点服务。
     */
    @Autowired
    private MountPointService mountPointService;

    /**
     * 文件系统路由解析器。
     */
    @Autowired
    private DiskFileSystemRouteResolver routeResolver;

    /**
     * 主文件系统执行组件。
     */
    @Autowired
    private DefaultFileSystemMainExecutor mainExecutor;

    /**
     * 元数据操作组件。
     */
    @Autowired
    private FileSystemMetadataOperator metadataOperator;

    /**
     * 根据请求路径匹配对应文件系统。
     *
     * @param uid 用户ID
     * @param path 请求路径
     * @return 匹配结果
     */
    private FileSystemRouteContext matchFileSystem(long uid, String path) {
        return routeResolver.matchFileSystem(uid, path);
    }

    /**
     * 校验目标完整路径是否与挂载点路径冲突。
     *
     * @param uid 用户ID
     * @param path 目标目录路径
     * @param name 文件名
     */
    private void validateNotMountPointPath(long uid, String path, String name) {
        String fullPath = StringUtils.appendPath(path, name);
        FileSystemRouteContext routeContext = matchFileSystem(uid, fullPath);
        if (routeContext.matchesMountPath(fullPath)) {
            throw new JsonException(FileSystemError.MOUNT_POINT_EXIST);
        }
    }

    @Override
    public List<FileInfo> getUserFileList(long uid, String path, @Nullable Collection<String> nameList) throws IOException {
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        if (routeContext.usesMainMetadata()) {
            return mainExecutor.getUserFileList(uid, path, nameList);
        }
        return routeContext.requireDelegateFileSystem().getUserFileList(uid, routeContext.getResolvedPath(), nameList);
    }

    @Override
    public Resource getThumbnail(long uid, String path, String name) throws IOException {
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        if (routeContext.isMainRoute()) {
            return mainExecutor.getThumbnail(uid, path, name);
        }
        return routeContext.requireDelegateFileSystem().getThumbnail(uid, routeContext.getResolvedPath(), name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quickSave(long uid, String path, String name, String md5) throws IOException {
        validateNotMountPointPath(uid, path, name);
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        boolean success;
        if (routeContext.isMainRoute()) {
            success = mainExecutor.quickSave(uid, path, name, md5);
        } else {
            success = routeContext.requireDelegateFileSystem().quickSave(uid, routeContext.getResolvedPath(), name, md5);
        }
        if (success && routeContext.requiresMainMetadataSync()) {
            syncProxyQuickSaveRecord(routeContext, uid, path, name);
        }
        if (success) {
            publishFileStoreEvent(routeContext.getFileSystemOr(this), uid, routeContext.getDelegatePath(path), name,
                    StringUtils.appendPath(path, name), null);
        }
        return success;
    }

    @Override
    public boolean exist(long uid, String path) throws IOException {
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        if (routeContext.isMountRoot()) {
            return true;
        }
        if (routeContext.usesMainMetadata()) {
            return mainExecutor.exist(uid, path);
        }
        return routeContext.requireDelegateFileSystem().exist(uid, routeContext.getResolvedPath());
    }

    @Override
    public Resource getResource(long uid, String path, String name) throws IOException {
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        if (routeContext.isMainRoute()) {
            return mainExecutor.getResource(uid, path, name);
        }
        return routeContext.requireDelegateFileSystem().getResource(uid, routeContext.getResolvedPath(), name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdirs(long uid, String path) throws IOException {
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        if (routeContext.matchesMountPath(path)) {
            throw new JsonException(FileSystemError.MOUNT_POINT_EXIST);
        }
        String dirNodeId;
        if (routeContext.isMainRoute()) {
            dirNodeId = mainExecutor.mkdirs(uid, path);
        } else {
            dirNodeId = routeContext.requireDelegateFileSystem().mkdirs(uid, routeContext.getResolvedPath());
        }
        if (routeContext.requiresMainMetadataSync()) {
            metadataOperator.mkdirs(uid, path, true);
        }
        eventPublisher.publishEvent(new MkdirEvent(this, uid, path, dirNodeId));
        return dirNodeId;
    }

    @Override
    public Resource getResourceByMd5(String md5) throws IOException {
        List<FileInfo> files = metadataOperator.getFileInfoByMd5(md5, 1);
        if (files.isEmpty()) {
            return null;
        }
        FileInfo fileInfo = files.getFirst();
        String path = metadataOperator.getPathByNodeId(fileInfo.getUid(), fileInfo.getNode())
                .orElseThrow(() -> new JsonException(FileSystemError.NODE_NOT_FOUND));
        fileInfo.setPath(StringUtils.appendPath(path, fileInfo.getName()));
        Resource resource = getResource(fileInfo.getUid(), path, fileInfo.getName());
        if (resource == null) {
            return null;
        }
        return ResourceUtils.bindFileInfo(resource, fileInfo);
    }

    /**
     * 同步代理挂载点 quickSave 后的文件记录。
     *
     * @param routeContext 文件系统路由上下文
     * @param uid 用户ID
     * @param path 目标目录
     * @param name 文件名
     */
    private void syncProxyQuickSaveRecord(FileSystemRouteContext routeContext, long uid, String path, String name) throws IOException {
        List<FileInfo> fileInfos = routeContext.requireDelegateFileSystem().getUserFileList(uid, routeContext.getResolvedPath(), Collections.singletonList(name));
        if (fileInfos == null || fileInfos.isEmpty()) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND, StringUtils.appendPath(path, name));
        }
        FileInfo savedFile = ObjectUtils.clone(fileInfos.getFirst(), FileInfo::new);
        savedFile.setUid(uid);
        savedFile.setId(null);
        savedFile.setNode(null);
        metadataOperator.saveRecord(savedFile, path);
    }

    /**
     * 发布文件保存事件。
     *
     * @param diskFileSystem 目标文件系统
     * @param uid 用户ID
     * @param delegatePath 文件在目标文件系统中的目录路径
     * @param name 文件名
     * @param fullPath 文件在统一文件系统中的完整路径
     * @param fileInfo 已知文件信息，可为null
     */
    private void publishFileStoreEvent(DiskFileSystem diskFileSystem, Long uid, String delegatePath, String name,
                                       String fullPath, FileInfo fileInfo) {
        eventPublisher.publishEvent(new FileStoreEvent(this, uid, fullPath, () -> {
            try {
                if (fileInfo != null) {
                    fileInfo.setPath(fullPath);
                    return fileInfo;
                }
                List<FileInfo> result = diskFileSystem.getUserFileList(uid, delegatePath, Collections.singletonList(name));
                if (result == null || result.isEmpty()) {
                    log.warn("{} 文件保存事件中未查询到文件信息 uid: {} fullPath: {}", LOG_PREFIX, uid, fullPath);
                    return null;
                }
                FileInfo stored = result.getFirst();
                stored.setPath(fullPath);
                return stored;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    /**
     * 发布文件移动事件。
     *
     * @param sourcePath 原路径
     * @param targetPath 目标路径
     */
    private void publishFileMoveEvent(String sourcePath, String targetPath) {
        eventPublisher.publishEvent(new FileMoveEvent(this, sourcePath, targetPath));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copy(SimpleFileTransferParam param, FileTransferCallback callback) throws IOException {
        doCopyInternal(param, callback, 0);
    }

    /**
     * 核心复制逻辑，支持跨文件系统复制与挂载点记录同步。
     *
     * @param param 复制参数
     * @param callback 复制回调
     * @param depth 当前递归深度
     */
    private void doCopyInternal(SimpleFileTransferParam param, FileTransferCallback callback, int depth) throws IOException {
        if (depth > 32) {
            throw new JsonException(FileSystemError.DIR_TOO_DEPTH, "目录深度超过32");
        }
        if (Objects.equals(param.getSourceUid(), param.getTargetUid())) {
            if (CollectionUtils.isEmpty(param.getFiles())) {
                if (PathUtils.isSubDir(param.getSourcePath(), param.getTargetPath())) {
                    throw new JsonException(FileSystemError.TARGET_IS_SUB_DIR, param.getSourcePath());
                }
            } else {
                boolean anyMatch = param.getFiles().stream().anyMatch(fileName -> {
                    String targetFullPath = StringUtils.appendPath(param.getTargetPath(), fileName);
                    String sourceFullPath = StringUtils.appendPath(param.getSourcePath(), fileName);
                    return PathUtils.isSubDir(sourceFullPath, targetFullPath);
                });
                if (anyMatch) {
                    throw new JsonException(FileSystemError.TARGET_IS_SUB_DIR, param.getSourcePath());
                }
            }
        }
        Long sourceUid = param.getSourceUid();
        String sourcePath = param.getSourcePath();
        Long targetUid = param.getTargetUid();
        String targetPath = param.getTargetPath();
        Boolean overwrite = param.getIsOverwrite();
        if (sourceUid == null || targetUid == null || sourcePath == null || targetPath == null) {
            throw new IllegalArgumentException("sourceUid, targetUid, sourcePath, targetPath 不能为 null");
        }

        FileSystemRouteContext sourceRoute = matchFileSystem(sourceUid, sourcePath);
        FileSystemRouteContext targetRoute = matchFileSystem(targetUid, targetPath);
        if (sourceRoute.sameFileSystem(targetRoute)) {
            if (callback != null && callback.shouldInterrupt()) {
                return;
            }
            if (sourceRoute.isMainRoute()) {
                mainExecutor.copy(param, callback);
                return;
            }
            if (sourceRoute.requiresMainMetadataSync()) {
                if (callback != null) {
                    callback.onAdditionalEvent(UpdateFileRecordStartEvent.of());
                }
                metadataOperator.copy(param, callback);
                if (callback != null) {
                    callback.onAdditionalEvent(UpdateFileRecordCompleteEvent.of(FileTransferItem.builder()
                            .from(param.getSourcePath())
                            .to(param.getTargetPath())
                            .build()));
                }
            }
            sourceRoute.requireDelegateFileSystem().copy(SimpleFileTransferParam.builder()
                    .sourceUid(sourceUid)
                    .sourcePath(sourceRoute.getResolvedPath())
                    .files(param.getFiles())
                    .targetUid(targetUid)
                    .targetPath(targetRoute.getResolvedPath())
                    .isOverwrite(overwrite)
                    .build(), callback);
            return;
        }

        Lazy<String> nodeId = Lazy.of(() -> metadataOperator.getNodeIdByPath(targetUid, targetPath)
                .orElseThrow(() -> new JsonException(404, "路径" + targetPath + "节点信息丢失")));
        List<FileInfo> sourceFileList = ObjectUtils.cloneListElement(
                this.getUserFileList(sourceUid, sourcePath, param.getFiles()),
                FileInfo::new
        );
        List<String> sourceNames = sourceFileList.stream().map(FileInfo::getName).toList();
        Map<String, FileInfo> targetExistFileMap = this.getUserFileList(targetUid, targetPath, sourceNames)
                .stream()
                .collect(Collectors.toMap(FileInfo::getName, file -> ObjectUtils.clone(file, FileInfo::new)));

        for (FileInfo sourceFile : sourceFileList) {
            if (callback != null && callback.shouldInterrupt()) {
                return;
            }
            sourceFile.setUid(targetUid);
            sourceFile.setPath(targetRoute.getDelegatePath(targetPath));
            if (targetRoute.requiresMainMetadataSync()) {
                sourceFile.setNode(nodeId.get());
                sourceFile.setIsMount(true);
            }

            FileInfo existFile = targetExistFileMap.get(sourceFile.getName());
            if (existFile != null && existFile.isDir() != sourceFile.isDir()) {
                if (sourceFile.isFile()) {
                    throw new JsonException(FileSystemError.NOT_ALLOW_FILE_OVERWRITE_DIR);
                } else {
                    throw new JsonException(FileSystemError.NOT_ALLOW_DIR_OVERWRITE_FILE);
                }
            }

            FileTransferItem transferRecord = FileTransferItem.builder()
                    .from(StringUtils.appendPath(sourcePath, sourceFile.getName()))
                    .to(StringUtils.appendPath(targetPath, sourceFile.getName()))
                    .fileInfo(sourceFile)
                    .total(sourceFile.isDir() ? 0 : sourceFile.getSize())
                    .loaded(0L)
                    .build();
            if (sourceFile.isFile()) {
                if (callback != null) {
                    callback.onFileStart(transferRecord);
                }
                if (!Boolean.TRUE.equals(param.getIsOverwrite()) && existFile != null) {
                    transferRecord.setIsSkip(true);
                    if (callback != null) {
                        callback.onFileComplete(transferRecord);
                    }
                    continue;
                }
                if (callback != null && callback.shouldInterrupt()) {
                    return;
                }
                Resource resource = getMatchedResource(sourceRoute, sourceUid, sourceRoute.getResolvedPath(), sourceFile.getName());
                if (resource == null) {
                    throw new JsonException(FileSystemError.FILE_NOT_FOUND, StringUtils.appendPath(sourcePath, sourceFile.getName()));
                }
                FileInfo targetFile = new FileInfo();
                BeanUtils.copyProperties(sourceFile, targetFile);
                targetFile.setStreamSource(resource);
                targetFile.setId(null);
                targetFile.setNode(null);
                saveFileToMatchedFileSystem(targetRoute, param.getTargetPath(), targetFile, os -> {
                    try (InputStream is = resource.getInputStream()) {
                        return StreamUtils.copyStreamAndComputeMd5(is, os, sourceFile.getMd5(), (buffer, len) -> {
                            Objects.requireNonNull(buffer);
                            transferRecord.setLoaded(transferRecord.getLoaded() + len);
                        }).applyTo(targetFile);
                    }
                });
                if (callback != null) {
                    callback.onFileComplete(transferRecord);
                }
            } else {
                String nextSourcePath = StringUtils.appendPath(sourcePath, sourceFile.getName());
                String nextTargetPath = StringUtils.appendPath(targetPath, sourceFile.getName());
                if (callback != null) {
                    callback.onDirStart(nextTargetPath);
                }
                this.mkdir(targetUid, targetPath, sourceFile.getName());
                doCopyInternal(SimpleFileTransferParam.builder()
                        .sourceUid(sourceUid)
                        .sourcePath(nextSourcePath)
                        .targetUid(targetUid)
                        .targetPath(nextTargetPath)
                        .isOverwrite(param.getIsOverwrite())
                        .build(), callback, depth + 1);
                if (callback != null) {
                    callback.onDirComplete(nextTargetPath);
                }
            }
        }
    }

    /**
     * 获取匹配文件系统上的文件资源。
     *
     * @param routeContext 路由上下文
     * @param uid 用户ID
     * @param delegatePath 实际文件系统中的目录路径
     * @param name 文件名
     * @return 文件资源
     */
    private Resource getMatchedResource(FileSystemRouteContext routeContext, long uid, String delegatePath, String name) throws IOException {
        if (routeContext.isMainRoute()) {
            return mainExecutor.getResource(uid, delegatePath, name);
        }
        return routeContext.requireDelegateFileSystem().getResource(uid, delegatePath, name);
    }

    /**
     * 向匹配到的文件系统写入文件，并在代理挂载点下补充文件记录。
     *
     * @param routeContext 路由上下文
     * @param originPath 原始目录路径
     * @param fileInfo 文件信息
     * @param streamConsumer 输出流消费函数
     */
    private void saveFileToMatchedFileSystem(FileSystemRouteContext routeContext, String originPath, FileInfo fileInfo,
                                             OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        if (routeContext.isMainRoute()) {
            mainExecutor.saveFileByStream(fileInfo, routeContext.getResolvedPath(), streamConsumer);
            return;
        }
        routeContext.requireDelegateFileSystem().saveFileByStream(fileInfo, routeContext.getResolvedPath(), streamConsumer);
        if (routeContext.requiresMainMetadataSync()) {
            metadataOperator.saveRecord(fileInfo, originPath);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(long uid, String source, String target, String name, boolean overwrite) throws IOException {
        String fullSourcePath = StringUtils.appendPath(source, name);
        String fullTargetPath = StringUtils.appendPath(target, name);
        FileSystemRouteContext targetRoute = matchFileSystem(uid, target);
        FileSystemRouteContext sourceRoute = matchFileSystem(uid, source);
        FileSystemRouteContext sourceFullRoute = matchFileSystem(uid, fullSourcePath);
        FileSystemRouteContext targetFullRoute = matchFileSystem(uid, fullTargetPath);

        List<MountPoint> mountPoints = null;
        Resource resource = getMatchedResource(sourceRoute, uid, sourceRoute.getResolvedPath(), name);
        if (resource == null) {
            mountPoints = mountPointService.listByPath(uid, fullSourcePath);
            if (targetRoute.isMountRoute() || targetFullRoute.matchesMountPath(fullTargetPath)) {
                if (mountPoints != null && !mountPoints.isEmpty()) {
                    throw new JsonException("目录包含挂载点，不能移动到其他挂载点下");
                }
                if (sourceFullRoute.matchesMountPath(fullSourcePath)) {
                    throw new JsonException("挂载点不允许移动到其他挂载点下");
                }
            }

            if (sourceFullRoute.matchesMountPath(fullSourcePath)) {
                String nodeId = metadataOperator.getNodeIdByPath(uid, target)
                        .orElseThrow(() -> new JsonException(FileSystemError.NODE_NOT_FOUND));
                MountPoint mountPoint = sourceFullRoute.requireMountPoint();
                mountPoint.setNid(nodeId);
                try {
                    mountPointService.saveMountPoint(mountPoint);
                    publishFileMoveEvent(fullSourcePath, fullTargetPath);
                    return;
                } catch (FileSystemParameterException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (sourceRoute.sameFileSystem(targetRoute)) {
            if (sourceRoute.isMainRoute()) {
                mainExecutor.move(uid, source, target, name, overwrite);
            } else {
                sourceRoute.requireDelegateFileSystem().move(uid, sourceRoute.getResolvedPath(), targetRoute.getResolvedPath(), name, overwrite);
                if (sourceRoute.requiresMainMetadataSync()) {
                    metadataOperator.move(uid, source, target, name, overwrite);
                }
            }
        } else {
            doCopyInternal(SimpleFileTransferParam.builder()
                    .sourceUid(uid)
                    .sourcePath(source)
                    .files(List.of(name))
                    .targetUid(uid)
                    .targetPath(target)
                    .isOverwrite(overwrite)
                    .build(), null, 0);
            doDeleteFile(uid, source, Collections.singletonList(name));
        }

        if (mountPoints != null && !mountPoints.isEmpty()) {
            mountPointService.clearCache(uid);
        }
        publishFileMoveEvent(fullSourcePath, fullTargetPath);
    }

    @Override
    public List<FileInfo>[] getUserFileList(long uid, String path) throws IOException {
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        List<FileInfo>[] result;
        if (routeContext.usesMainMetadata()) {
            result = mainExecutor.getUserFileList(uid, path);
        } else {
            result = routeContext.requireDelegateFileSystem().getUserFileList(uid, routeContext.getResolvedPath());
        }
        if (routeContext.isMountRoute()) {
            markMountFiles(uid, result);
        }
        return result;
    }

    /**
     * 为挂载点返回的文件列表补充挂载标识。
     *
     * @param uid 用户ID
     * @param fileLists 文件列表数组
     */
    private void markMountFiles(long uid, List<FileInfo>[] fileLists) {
        for (List<FileInfo> fileList : fileLists) {
            for (FileInfo fileInfo : fileList) {
                fileInfo.setIsMount(true);
                fileInfo.setUid(uid);
            }
        }
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(long uid, boolean reverse) {
        return mainExecutor.collectFiles(uid, reverse);
    }

    @Override
    public List<FileInfo>[] getUserFileListByNodeId(long uid, String nodeId) {
        return mainExecutor.getUserFileListByNodeId(uid, nodeId);
    }

    @Override
    public CommonPageInfo<FileInfo> search(long uid, String key, Integer page) {
        return search(uid, key, page, 10);
    }

    @Override
    public CommonPageInfo<FileInfo> search(long uid, String key, Integer page, Integer size) {
        return mainExecutor.search(uid, key, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveToSaveFile(long uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        if (fileInfo == null || fileInfo.getName() == null) {
            throw new IllegalArgumentException("fileInfo/name 不能为空");
        }
        validateNotMountPointPath(uid, path, fileInfo.getName());
        fileInfo.setUid(uid);
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        if (routeContext.isMainRoute()) {
            mainExecutor.moveToSaveFile(uid, nativeFilePath, path, fileInfo);
        } else {
            routeContext.requireDelegateFileSystem().moveToSaveFile(uid, nativeFilePath, routeContext.getResolvedPath(), fileInfo);
            if (routeContext.requiresMainMetadataSync()) {
                metadataOperator.saveRecord(fileInfo, path);
            }
        }
        publishFileStoreEvent(routeContext.getFileSystemOr(this), uid, routeContext.getDelegatePath(path),
                fileInfo.getName(), StringUtils.appendPath(path, fileInfo.getName()), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveFiles(List<FileInfo> fileInfos, @Nullable FileTransferCallback callback) throws IOException {
        if (CollectionUtils.isEmpty(fileInfos)) {
            return;
        }
        class BatchGroup {
            private final Long uid;
            private final String originPath;
            private final List<FileInfo> sourceFiles = new ArrayList<>();

            private BatchGroup(Long uid, String originPath) {
                this.uid = uid;
                this.originPath = originPath;
            }
        }

        LinkedHashMap<String, BatchGroup> grouped = new LinkedHashMap<>();
        for (FileInfo fileInfo : fileInfos) {
            if (fileInfo == null
                    || fileInfo.getUid() == null
                    || fileInfo.getPath() == null
                    || fileInfo.getName() == null
                    || fileInfo.getStreamSource() == null) {
                throw new IllegalArgumentException("batchSaveFiles param invalid: uid/path/name/streamSource must not be null");
            }
            validateNotMountPointPath(fileInfo.getUid(), fileInfo.getPath(), fileInfo.getName());
            String key = fileInfo.getUid() + ":" + fileInfo.getPath();
            BatchGroup group = grouped.get(key);
            if (group == null) {
                group = new BatchGroup(fileInfo.getUid(), fileInfo.getPath());
                grouped.put(key, group);
            }
            group.sourceFiles.add(fileInfo);
        }

        for (BatchGroup group : grouped.values()) {
            FileSystemRouteContext routeContext = matchFileSystem(group.uid, group.originPath);
            List<FileInfo> delegateFiles = new ArrayList<>(group.sourceFiles.size());
            for (FileInfo sourceFile : group.sourceFiles) {
                FileInfo delegateFile = new FileInfo();
                BeanUtils.copyProperties(sourceFile, delegateFile);
                delegateFile.setPath(routeContext.getDelegatePath(group.originPath));
                delegateFiles.add(delegateFile);
            }

            if (routeContext.isMainRoute()) {
                mainExecutor.batchSaveFiles(delegateFiles, callback);
            } else {
                routeContext.requireDelegateFileSystem().batchSaveFiles(delegateFiles, callback);
                if (routeContext.requiresMainMetadataSync()) {
                    metadataOperator.batchSaveFileInSameDirectory(group.uid, group.originPath, delegateFiles);
                }
            }
            for (FileInfo fileInfo : delegateFiles) {
                publishFileStoreEvent(routeContext.getFileSystemOr(this), fileInfo.getUid(),
                        routeContext.getDelegatePath(group.originPath), fileInfo.getName(),
                        StringUtils.appendPath(group.originPath, fileInfo.getName()), fileInfo);
            }
        }
    }

    @Override
    public void saveFileByStream(FileInfo file, String savePath, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        if (!FileNameValidator.valid(file.getName())) {
            throw new IllegalArgumentException("非法文件名，不可包含/\\<>?|:换行符，回车符或文件名为..");
        }
        validateNotMountPointPath(file.getUid(), savePath, file.getName());
        FileSystemRouteContext routeContext = matchFileSystem(file.getUid(), savePath);
        if (routeContext.isMainRoute()) {
            mainExecutor.saveFileByStream(file, savePath, streamConsumer);
        } else {
            routeContext.requireDelegateFileSystem().saveFileByStream(file, routeContext.getResolvedPath(), streamConsumer);
            if (routeContext.requiresMainMetadataSync()) {
                metadataOperator.saveRecord(file, savePath);
            }
        }
        publishFileStoreEvent(routeContext.getFileSystemOr(this), file.getUid(), routeContext.getDelegatePath(savePath),
                file.getName(), StringUtils.appendPath(savePath, file.getName()), file);
    }

    @Override
    public void mkdir(long uid, String path, String name) throws IOException {
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        if (routeContext.isMainRoute()) {
            mainExecutor.mkdir(uid, path, name);
        } else {
            routeContext.requireDelegateFileSystem().mkdir(uid, routeContext.getResolvedPath(), name);
            if (routeContext.requiresMainMetadataSync()) {
                metadataOperator.mkdirs(uid, StringUtils.appendPath(path, name), true);
            }
        }
    }

    /**
     * 执行删除逻辑，并在必要时移除挂载点元数据。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param names 待删除文件名列表
     * @return 删除数量
     */
    private long doDeleteFile(long uid, String path, List<String> names) throws IOException {
        FileSystemRouteContext routeContext = matchFileSystem(uid, path);
        Map<String, MountPoint> mountPointMap = mountPointService.findMountPointPathByUid(uid);
        Map<Long, MountPoint> needRemoveMountPointMap = names.stream()
                .flatMap(name -> mountPointMap.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith(StringUtils.appendPath(path, name)))
                        .map(Map.Entry::getValue))
                .collect(Collectors.toMap(MountPoint::getId, Function.identity(), DefaultFileSystem::keepExistingValue));
        if (!needRemoveMountPointMap.isEmpty()) {
            log.info("{}:删除的路径中存在以下挂载点需要删除：{}", LOG_PREFIX, needRemoveMountPointMap.keySet());
            mountPointService.batchRemove(uid, needRemoveMountPointMap.keySet());
        }
        if (routeContext.isMainRoute()) {
            return mainExecutor.deleteFile(uid, path, names);
        }
        if (routeContext.requiresMainMetadataSync()) {
            metadataOperator.deleteRecords(uid, path, names);
        }
        return routeContext.requireDelegateFileSystem().deleteFile(uid, routeContext.getResolvedPath(), names);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long deleteFile(long uid, String path, List<String> name) throws IOException {
        long result = doDeleteFile(uid, path, name);
        eventPublisher.publishEvent(new FileDeleteEvent(this, uid, path, name));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(long uid, String path, String name, String newName) throws IOException {
        String originPath = StringUtils.appendPath(path, name);
        FileSystemRouteContext routeContext = matchFileSystem(uid, originPath);
        if (routeContext.matchesMountPath(originPath)) {
            if (exist(uid, StringUtils.appendPath(path, newName))) {
                throw new JsonException(FileSystemError.FILE_EXIST);
            }
            try {
                MountPoint mountPoint = routeContext.requireMountPoint();
                mountPoint.setName(newName);
                mountPointService.saveMountPoint(mountPoint);
            } catch (FileSystemParameterException e) {
                log.error("{}重命名文件时发生错误", LOG_PREFIX, e);
                throw new JsonException(e.getMessage());
            }
            return;
        }

        routeContext = matchFileSystem(uid, path);
        if (routeContext.isMainRoute()) {
            mainExecutor.rename(uid, path, name, newName);
        } else {
            if (routeContext.requiresMainMetadataSync()) {
                metadataOperator.rename(uid, path, name, newName);
            }
            routeContext.requireDelegateFileSystem().rename(uid, routeContext.getResolvedPath(), name, newName);
        }
    }

    @Override
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        for (String name : names) {
            String filePath = StringUtils.appendPath(path, name);
            FileSystemRouteContext routeContext = matchFileSystem(uid, filePath);
            if (routeContext.isMainRoute()) {
                mainExecutor.updateTime(uid, path, Collections.singletonList(name), attribute);
            } else {
                if (routeContext.requiresMainMetadataSync()) {
                    metadataOperator.updateTime(uid, path, Collections.singletonList(name), attribute);
                }
                String parentPath = PathUtils.getParentPath(routeContext.getResolvedPath());
                String delegateName = PathUtils.getLastNode(routeContext.getResolvedPath());
                routeContext.requireDelegateFileSystem().updateTime(uid, parentPath, Collections.singletonList(delegateName), attribute);
            }
        }
    }

    /**
     * 在收集重复键时保留首个值。
     *
     * @param existing 已存在的值
     * @param replacement 重复值
     * @param <T> 值类型
     * @return 已存在的值
     */
    private static <T> T keepExistingValue(T existing, T replacement) {
        Objects.requireNonNull(replacement);
        return existing;
    }

    @Override
    public void registerFeature(HelloService helloService) {
        helloService.appendFeatureDetail(FeatureName.ARCHIVE_TYPE, "zip");
        helloService.appendFeatureDetail(FeatureName.EXTRACT_ARCHIVE_TYPE, "zip");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " - Managed";
    }

    @Override
    public List<FileSystemStatus> getStatus() {
        return mainExecutor.getStatus();
    }
}
