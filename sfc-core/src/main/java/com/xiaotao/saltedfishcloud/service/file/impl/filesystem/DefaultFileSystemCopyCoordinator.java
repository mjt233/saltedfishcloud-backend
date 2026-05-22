package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.model.progress.event.UpdateFileRecordCompleteEvent;
import com.xiaotao.saltedfishcloud.model.progress.event.UpdateFileRecordStartEvent;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StreamCopyResult;
import com.xiaotao.saltedfishcloud.utils.StreamUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 默认文件系统复制编排组件，负责处理主文件系统、挂载文件系统以及跨文件系统之间的复制流程。
 */
@Component
public class DefaultFileSystemCopyCoordinator {
    /**
     * 复制递归最大深度。
     */
    private static final int MAX_COPY_DEPTH = 32;

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
     * 执行复制流程。
     *
     * @param param 复制参数
     * @param callback 复制回调，可为null
     * @throws IOException 文件操作异常
     */
    public void copy(SimpleFileTransferParam param, @Nullable FileTransferCallback callback) throws IOException {
        doCopyInternal(param, callback, 0);
    }

    /**
     * 递归执行复制逻辑，支持同文件系统直拷与跨文件系统复制。
     *
     * @param param 复制参数
     * @param callback 复制回调，可为null
     * @param depth 当前递归深度
     * @throws IOException 文件操作异常
     */
    private void doCopyInternal(SimpleFileTransferParam param, @Nullable FileTransferCallback callback, int depth) throws IOException {
        validateCopyDepth(depth);

        Long sourceUid = param.getSourceUid();
        String sourcePath = param.getSourcePath();
        Long targetUid = param.getTargetUid();
        String targetPath = param.getTargetPath();
        validateCopyParameter(sourceUid, sourcePath, targetUid, targetPath);
        validateNotTargetSubDir(param, sourceUid, targetUid, sourcePath, targetPath);

        FileSystemRouteContext sourceRoute = routeResolver.matchFileSystem(sourceUid, sourcePath);
        FileSystemRouteContext targetRoute = routeResolver.matchFileSystem(targetUid, targetPath);
        if (tryCopyWithinSameFileSystem(param, callback, sourceUid, targetUid, sourcePath, targetPath, sourceRoute, targetRoute)) {
            return;
        }
        copyAcrossFileSystems(param, callback, depth, sourceUid, sourcePath, targetUid, targetPath, sourceRoute, targetRoute);
    }

    /**
     * 校验复制递归深度。
     *
     * @param depth 当前递归深度
     */
    private void validateCopyDepth(int depth) {
        if (depth > MAX_COPY_DEPTH) {
            throw new JsonException(FileSystemError.DIR_TOO_DEPTH, "目录深度超过32");
        }
    }

    /**
     * 校验复制请求中的必填参数。
     *
     * @param sourceUid 源用户ID
     * @param sourcePath 源目录路径
     * @param targetUid 目标用户ID
     * @param targetPath 目标目录路径
     */
    private void validateCopyParameter(@Nullable Long sourceUid,
                                       @Nullable String sourcePath,
                                       @Nullable Long targetUid,
                                       @Nullable String targetPath) {
        if (sourceUid == null || targetUid == null || sourcePath == null || targetPath == null) {
            throw new IllegalArgumentException("sourceUid, targetUid, sourcePath, targetPath 不能为 null");
        }
    }

    /**
     * 校验目标路径是否落在源目录之下。
     *
     * @param param 复制参数
     * @param sourceUid 源用户ID
     * @param targetUid 目标用户ID
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     */
    private void validateNotTargetSubDir(SimpleFileTransferParam param,
                                         long sourceUid,
                                         long targetUid,
                                         String sourcePath,
                                         String targetPath) {
        if (sourceUid != targetUid) {
            return;
        }
        if (CollectionUtils.isEmpty(param.getFiles())) {
            if (PathUtils.isSubDir(sourcePath, targetPath)) {
                throw new JsonException(FileSystemError.TARGET_IS_SUB_DIR, sourcePath);
            }
            return;
        }
        boolean anyMatch = param.getFiles().stream().anyMatch(fileName -> {
            String targetFullPath = StringUtils.appendPath(targetPath, fileName);
            String sourceFullPath = StringUtils.appendPath(sourcePath, fileName);
            return PathUtils.isSubDir(sourceFullPath, targetFullPath);
        });
        if (anyMatch) {
            throw new JsonException(FileSystemError.TARGET_IS_SUB_DIR, sourcePath);
        }
    }

    /**
     * 尝试走同文件系统复制快速路径。
     *
     * @param param 复制参数
     * @param callback 复制回调，可为null
     * @param sourceUid 源用户ID
     * @param targetUid 目标用户ID
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @param sourceRoute 源路由上下文
     * @param targetRoute 目标路由上下文
     * @return 已处理返回true，否则返回false
     * @throws IOException 文件操作异常
     */
    private boolean tryCopyWithinSameFileSystem(SimpleFileTransferParam param,
                                                @Nullable FileTransferCallback callback,
                                                long sourceUid,
                                                long targetUid,
                                                String sourcePath,
                                                String targetPath,
                                                FileSystemRouteContext sourceRoute,
                                                FileSystemRouteContext targetRoute) throws IOException {
        if (!sourceRoute.sameFileSystem(targetRoute)) {
            return false;
        }
        if (callback != null && callback.shouldInterrupt()) {
            return true;
        }
        if (sourceRoute.isMainRoute()) {
            mainExecutor.copy(param, callback);
            return true;
        }
        if (sourceRoute.requiresMainMetadataSync()) {
            notifyMetadataCopyStart(callback);
            metadataOperator.copy(param, callback);
            notifyMetadataCopyComplete(callback, sourcePath, targetPath);
        }
        sourceRoute.requireDelegateFileSystem().copy(createDelegateCopyParam(param, sourceUid, targetUid, sourceRoute, targetRoute), callback);
        return true;
    }

    /**
     * 发送主文件系统记录复制开始事件。
     *
     * @param callback 复制回调，可为null
     */
    private void notifyMetadataCopyStart(@Nullable FileTransferCallback callback) {
        if (callback != null) {
            callback.onAdditionalEvent(UpdateFileRecordStartEvent.of());
        }
    }

    /**
     * 发送主文件系统记录复制完成事件。
     *
     * @param callback 复制回调，可为null
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     */
    private void notifyMetadataCopyComplete(@Nullable FileTransferCallback callback, String sourcePath, String targetPath) {
        if (callback != null) {
            callback.onAdditionalEvent(UpdateFileRecordCompleteEvent.of(FileTransferItem.builder()
                    .from(sourcePath)
                    .to(targetPath)
                    .build()));
        }
    }

    /**
     * 构造委托文件系统使用的复制参数。
     *
     * @param param 原始复制参数
     * @param sourceUid 源用户ID
     * @param targetUid 目标用户ID
     * @param sourceRoute 源路由上下文
     * @param targetRoute 目标路由上下文
     * @return 委托文件系统使用的复制参数
     */
    private SimpleFileTransferParam createDelegateCopyParam(SimpleFileTransferParam param,
                                                            long sourceUid,
                                                            long targetUid,
                                                            FileSystemRouteContext sourceRoute,
                                                            FileSystemRouteContext targetRoute) {
        return SimpleFileTransferParam.builder()
                .sourceUid(sourceUid)
                .sourcePath(sourceRoute.getResolvedPath())
                .files(param.getFiles())
                .targetUid(targetUid)
                .targetPath(targetRoute.getResolvedPath())
                .isOverwrite(param.getIsOverwrite())
                .build();
    }

    /**
     * 执行跨文件系统复制。
     *
     * @param param 复制参数
     * @param callback 复制回调，可为null
     * @param depth 当前递归深度
     * @param sourceUid 源用户ID
     * @param sourcePath 源目录路径
     * @param targetUid 目标用户ID
     * @param targetPath 目标目录路径
     * @param sourceRoute 源路由上下文
     * @param targetRoute 目标路由上下文
     * @throws IOException 文件操作异常
     */
    private void copyAcrossFileSystems(SimpleFileTransferParam param,
                                       @Nullable FileTransferCallback callback,
                                       int depth,
                                       long sourceUid,
                                       String sourcePath,
                                       long targetUid,
                                       String targetPath,
                                       FileSystemRouteContext sourceRoute,
                                       FileSystemRouteContext targetRoute) throws IOException {
        Lazy<String> nodeId = Lazy.of(() -> metadataOperator.getNodeIdByPath(targetUid, targetPath)
                .orElseThrow(() -> new JsonException(404, "路径" + targetPath + "节点信息丢失")));
        List<FileInfo> sourceFileList = ObjectUtils.cloneListElement(listFiles(sourceUid, sourcePath, param.getFiles()), FileInfo::new);
        List<String> sourceNames = sourceFileList.stream().map(FileInfo::getName).toList();
        Map<String, FileInfo> targetExistFileMap = listFiles(targetUid, targetPath, sourceNames)
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
            validateTargetTypeConflict(sourceFile, existFile);
            if (sourceFile.isFile()) {
                copyFileAcrossFileSystems(param, callback, sourceUid, sourcePath, targetPath, sourceRoute, targetRoute, sourceFile, existFile);
            } else {
                copyDirectoryAcrossFileSystems(param, callback, depth, sourceUid, targetUid, sourcePath, targetPath, sourceFile);
            }
        }
    }

    /**
     * 校验目标已存在文件与源文件的类型冲突。
     *
     * @param sourceFile 源文件信息
     * @param existFile 目标已存在文件信息，可为null
     */
    private void validateTargetTypeConflict(FileInfo sourceFile, @Nullable FileInfo existFile) {
        if (existFile == null || existFile.isDir() == sourceFile.isDir()) {
            return;
        }
        if (sourceFile.isFile()) {
            throw new JsonException(FileSystemError.NOT_ALLOW_FILE_OVERWRITE_DIR);
        }
        throw new JsonException(FileSystemError.NOT_ALLOW_DIR_OVERWRITE_FILE);
    }

    /**
     * 执行跨文件系统文件复制。
     *
     * @param param 复制参数
     * @param callback 复制回调，可为null
     * @param sourceUid 源用户ID
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @param sourceRoute 源路由上下文
     * @param targetRoute 目标路由上下文
     * @param sourceFile 源文件信息
     * @param existFile 目标已存在文件信息，可为null
     * @throws IOException 文件操作异常
     */
    private void copyFileAcrossFileSystems(SimpleFileTransferParam param,
                                           @Nullable FileTransferCallback callback,
                                           long sourceUid,
                                           String sourcePath,
                                           String targetPath,
                                           FileSystemRouteContext sourceRoute,
                                           FileSystemRouteContext targetRoute,
                                           FileInfo sourceFile,
                                           @Nullable FileInfo existFile) throws IOException {
        FileTransferItem transferRecord = FileTransferItem.builder()
                .from(StringUtils.appendPath(sourcePath, sourceFile.getName()))
                .to(StringUtils.appendPath(targetPath, sourceFile.getName()))
                .fileInfo(sourceFile)
                .total(sourceFile.getSize())
                .loaded(0L)
                .build();
        if (callback != null) {
            callback.onFileStart(transferRecord);
        }
        if (!Boolean.TRUE.equals(param.getIsOverwrite()) && existFile != null) {
            transferRecord.setIsSkip(true);
            if (callback != null) {
                callback.onFileComplete(transferRecord);
            }
            return;
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
        saveFileToMatchedFileSystem(targetRoute, targetPath, targetFile, os -> copyFileStream(resource, sourceFile, targetFile, transferRecord, os));
        if (callback != null) {
            callback.onFileComplete(transferRecord);
        }
    }

    /**
     * 执行跨文件系统目录复制。
     *
     * @param param 复制参数
     * @param callback 复制回调，可为null
     * @param depth 当前递归深度
     * @param sourceUid 源用户ID
     * @param targetUid 目标用户ID
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @param sourceFile 源目录信息
     * @throws IOException 文件操作异常
     */
    private void copyDirectoryAcrossFileSystems(SimpleFileTransferParam param,
                                                @Nullable FileTransferCallback callback,
                                                int depth,
                                                long sourceUid,
                                                long targetUid,
                                                String sourcePath,
                                                String targetPath,
                                                FileInfo sourceFile) throws IOException {
        String nextSourcePath = StringUtils.appendPath(sourcePath, sourceFile.getName());
        String nextTargetPath = StringUtils.appendPath(targetPath, sourceFile.getName());
        if (callback != null) {
            callback.onDirStart(nextTargetPath);
        }
        mkdir(targetUid, targetPath, sourceFile.getName());
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

    /**
     * 复制文件流到目标输出流，并更新复制进度。
     *
     * @param resource 源文件资源
     * @param sourceFile 源文件信息
     * @param targetFile 目标文件信息
     * @param transferRecord 传输进度记录
     * @param outputStream 目标输出流
     * @return 写入后的文件信息
     * @throws IOException 文件操作异常
     */
    private StreamCopyResult copyFileStream(Resource resource,
                                            FileInfo sourceFile,
                                            FileInfo targetFile,
                                            FileTransferItem transferRecord,
                                            OutputStream outputStream) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyStreamAndComputeMd5(inputStream, outputStream, sourceFile.getMd5(), (buffer, len) -> {
                Objects.requireNonNull(buffer);
                transferRecord.setLoaded(transferRecord.getLoaded() + len);
            }).applyTo(targetFile);
        }
    }

    /**
     * 获取某个统一路径上的文件列表。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param nameList 文件名列表，可为null
     * @return 文件信息列表
     * @throws IOException 文件操作异常
     */
    private List<FileInfo> listFiles(long uid, String path, @Nullable Collection<String> nameList) throws IOException {
        FileSystemRouteContext routeContext = routeResolver.matchFileSystem(uid, path);
        if (routeContext.usesMainMetadata()) {
            return mainExecutor.getUserFileList(uid, path, nameList);
        }
        return routeContext.requireDelegateFileSystem().getUserFileList(uid, routeContext.getResolvedPath(), nameList);
    }

    /**
     * 获取匹配文件系统上的文件资源。
     *
     * @param routeContext 路由上下文
     * @param uid 用户ID
     * @param delegatePath 实际文件系统中的目录路径
     * @param name 文件名
     * @return 文件资源
     * @throws IOException 文件操作异常
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
     * @throws IOException 文件操作异常
     */
    private void saveFileToMatchedFileSystem(FileSystemRouteContext routeContext,
                                             String originPath,
                                             FileInfo fileInfo,
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

    /**
     * 在匹配到的目标文件系统中创建目录。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param name 目录名
     * @throws IOException 文件操作异常
     */
    private void mkdir(long uid, String path, String name) throws IOException {
        FileSystemRouteContext routeContext = routeResolver.matchFileSystem(uid, path);
        if (routeContext.isMainRoute()) {
            mainExecutor.mkdir(uid, path, name);
            return;
        }
        routeContext.requireDelegateFileSystem().mkdir(uid, routeContext.getResolvedPath(), name);
        if (routeContext.requiresMainMetadataSync()) {
            metadataOperator.mkdirs(uid, StringUtils.appendPath(path, name), true);
        }
    }
}
