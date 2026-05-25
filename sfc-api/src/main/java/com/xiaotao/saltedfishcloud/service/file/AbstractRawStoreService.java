package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveHandler;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 原始存储服务的抽象模板类。
 * 基于设定的路径和直接原始存储操作器提供文件存储能力。
 */
@Slf4j
public abstract class AbstractRawStoreService implements StoreService {
    private final static String LOG_TITLE = "Raw-Store";
    @Getter
    @Setter
    private int maxDepth = 64;

    protected final Storage handler;
    protected final CopyAndMoveHandler copyAndMoveHandler;
    protected FileResourceMd5Resolver md5Resolver;

    private volatile StoreService uniqueStoreService;

    public AbstractRawStoreService(Storage handler,
                                   FileResourceMd5Resolver md5Resolver
    ) {
        Assert.notNull(handler, "未能获取到存储操作器");
        this.handler = handler;
        copyAndMoveHandler = new CopyAndMoveHandlerImpl(handler, isMoveWithRecursion());
        this.md5Resolver = md5Resolver;

    }

    /**
     * 文件移动是否需要递归执行。
     * @return 若返回值为true，则通过递归逐个移动文件。若为false，则直接调用{@link CopyAndMoveHandler#moveFile(java.lang.String, java.lang.String, com.xiaotao.saltedfishcloud.model.progress.FileTransferItem)}方法进行移动。
     * 使用递归移动时，支持同名目录合并。
     *
     */
    public abstract boolean isMoveWithRecursion();

    @Override
    public void clear() throws IOException {
        handler.delete(StringUtils.appendPath(getPublicRoot()));
        handler.delete(StringUtils.appendPath(getStoreRoot(), "user_file"));
    }

    private static class CopyAndMoveHandlerImpl extends CopyAndMoveHandler {
        private final Storage handler;
        private final boolean moveWithRecursion;
        public CopyAndMoveHandlerImpl(Storage handler, boolean moveWithRecursion) {
            super(handler);
            this.handler = handler;
            this.moveWithRecursion = moveWithRecursion;
        }

        @Override
        protected boolean isMoveWithRecursion() {
            return moveWithRecursion;
        }

        @Override
        public boolean copyFile(String src, String dest, @Nullable FileTransferItem item) throws IOException {
            return handler.copy(src, dest, item);
        }

        @Override
        public boolean moveFile(String src, String dest, @Nullable FileTransferItem item) throws IOException {
            return handler.move(src, dest, item);
        }

        @Override
        protected boolean mkdir(String path) throws IOException {
            return handler.mkdirs(path);
        }

        @Override
        protected boolean rmdir(String path) throws IOException {
            return handler.delete(path);
        }
    }

    /**
     * 获取公共网盘根目录路径
     */
    public abstract String getPublicRoot();

    /**
     * 获取网盘资源存储根目录路径
     * 自定义个性化存储根目录路径：storeRoot + "/user_profile"
     *                私人网盘：storeRoot + "/user_file"
     *                临时目录：storeRoot + "/temp"
     *      文件总仓（唯一存储）：storeRoot + "/repo"
     */
    public abstract String getStoreRoot();

    public final String getUserProfileRoot(long uid) {
        return StringUtils.appendPath(getStoreRoot(), "user_profile", uid + "");
    }

    public final String getUserFileRoot(long uid) {
        if (uid == UserConstants.PUBLIC_USER_ID) {
            return getPublicRoot();
        } else {
            return StringUtils.appendPath(getStoreRoot(), "user_file", uid + "");
        }
    }

    public final String getTempRoot() {
        return StringUtils.appendPath(getStoreRoot(), "temp");
    }

    public final String getRepoRoot() {
        return StringUtils.appendPath(getStoreRoot(), "repo");
    }

    @Override
    public boolean isUnique() {
        return false;
    }

    @Override
    public StoreService getRawStoreService() {
        return this;
    }

    @Override
    public StoreService getUniqueStoreService() {
        if (md5Resolver == null) {
            throw new UnsupportedOperationException();
        }

        // 双重校验锁懒汉单例
        if (uniqueStoreService != null) {
            return uniqueStoreService;
        }
        synchronized (this) {
            if (uniqueStoreService != null) {
                return uniqueStoreService;
            }
            uniqueStoreService = new DefaultUniqueStoreService(
                    handler,
                    md5Resolver,
                    this
            );
        }
        return uniqueStoreService;
    }

    @Override
    public boolean canBrowse() {
        return true;
    }

    @Override
    public void storeByStream(FileInfo file, String savePath, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        final String root = getUserFileRoot(file.getUid());
        final String rawPath = StringUtils.appendPath(root, savePath, file.getName());
        final String tmpPath = rawPath + IdUtil.getId() + ".tmp";
        boolean isSuccess = false;
        try(OutputStream os = handler.newOutputStream(tmpPath)) {
            streamConsumer.accept(os).applyTo(file);
            os.close();
            isSuccess = true;
            handler.move(tmpPath, rawPath, null);
        } finally {
            if (!isSuccess) {
                handler.delete(tmpPath);
            }
        }
    }

    @Override
    public void rename(long uid, String path, String oldName, String newName) throws IOException {
        final String src = getPath(uid, path, oldName);
        handler.rename(src, newName);
    }

    @Override
    public boolean mkdir(long uid, String path, String name) throws IOException {
        return handler.mkdirs(getPath(uid, path, name));
    }

    @Override
    public long delete(long uid, String path, Collection<String> files) throws IOException {
        final String root = getPath(uid, path);
        for (String file : files) {
            if(!FileNameValidator.valid(file)) {
                throw new JsonException(CommonError.NOT_ALLOW_PATH);
            }
            handler.delete(StringUtils.appendPath(root, file));
        }
        return files.size();
    }

    /**
     * 获取指定UID用户的资源路径，将以用户的存储根为节点，往后拼接路径。
     * 安全保证：不会越出用户的存储根节点外
     * @param uid   用户ID，可以是公共网盘用户ID
     * @param path  路径节点集合
     * @return 资源完整存储路径
     */
    private String getPath(long uid, String...path) {
        String root = (uid == UserConstants.PUBLIC_USER_ID ?
                getPublicRoot() :
                StringUtils.appendPath(getUserFileRoot(uid))
        );
        PathBuilder builder = new PathBuilder();
        for (String s : path) {
            if (s != null) {
                builder.append(s);
            }
        }
        return StringUtils.appendPath(root, builder.toString());
    }

    @Override
    public List<FileInfo> lists(long uid, String path) throws IOException {
        if (!canBrowse()) {
            return Collections.emptyList();
        }
        final List<FileInfo> files = handler.listFiles(getPath(uid, path));
        final String prefixRoot = getUserFileRoot(uid);
        files.forEach(fileInfo -> {
            fileInfo.setUid(uid);
            fileInfo.setPath(StringUtils.removePrefix(prefixRoot, fileInfo.getPath()));
        });
        return files;
    }

    @Override
    public Resource getResource(long uid, String path, String name) throws IOException {
        String root;
        if (name == null) {
            root = getPath(uid, path);
        } else {
            root = getPath(uid, path, name);
        }
        log.debug("[{}]请求资源：{}", LOG_TITLE, root);
        return handler.getResource(root);
    }

    @Override
    public boolean exist(long uid, String path) throws IOException {
        return handler.exist(getPath(uid, path));
    }

    @Override
    public void moveToSave(long uid, Path nativePath, String diskPath, FileInfo fileInfo) throws IOException {
        try(final InputStream is = Files.newInputStream(nativePath)) {
            final FileInfo fileResource = FileInfo.getFromResource(new PathResource(nativePath), uid, fileInfo.getType());
            fileResource.setName(fileInfo.getName());
            store(uid, is, diskPath, fileResource);
            is.close();
            Files.delete(nativePath);
        }
    }

    @Override
    public void copy(SimpleFileTransferParam param, FileTransferCallback callback) throws IOException {
        List<String> sourceFileNames = Optional.ofNullable(param.getFiles())
                .filter(files -> !files.isEmpty())
                .orElseGet(() -> {
                    try {
                        return lists(param.getSourceUid(), param.getSourcePath()).stream().map(FileInfo::getName).toList();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        for (String sourceFileName : sourceFileNames) {
            final String src = StringUtils.appendPath(getUserFileRoot(param.getSourceUid()), param.getSourcePath(), sourceFileName);
            final String dst = StringUtils.appendPath(getUserFileRoot(param.getTargetUid()), param.getTargetPath(), sourceFileName);
            copyAndMoveHandler.copy(src, dst, param.getIsOverwrite(), callback);
        }
    }

    @Override
    public void move(long sourceUid, String source, long targetUid, String target, String name, boolean overwrite) throws IOException {
        final String src = StringUtils.appendPath(getUserFileRoot(sourceUid), source, name);
        final String dst = StringUtils.appendPath(getUserFileRoot(targetUid), target, name);
        final String dstParent = PathUtils.getParentPath(dst);

        if (!handler.exist(src)) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        if (!handler.exist(dstParent)) {
            handler.mkdirs(dstParent);
        }

        copyAndMoveHandler.move(src, dst, overwrite);
    }


    @Override
    public List<FileSystemStatus> getStatus() {
        return Collections.emptyList();
    }

    @Override
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        String parentPath = StringUtils.appendPath(getUserFileRoot(uid), path);
        this.handler.updateTime(parentPath, names, attribute);
    }

    @Override
    public Storage getStorageProvider() {
        return this.handler;
    }
}
