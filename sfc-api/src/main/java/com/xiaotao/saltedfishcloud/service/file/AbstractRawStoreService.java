package com.xiaotao.saltedfishcloud.service.file;

import com.sfc.constant.error.CommonError;
import com.sfc.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveHandler;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import com.xiaotao.saltedfishcloud.validator.FileValidator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 原始存储服务的抽象模板类，同时实现了{@link StoreService}和{@link CustomStoreService}。
 * 基于设定的路径和直接原始存储操作器提供文件存储能力。
 */
@Slf4j
public abstract class AbstractRawStoreService implements StoreService, CustomStoreService {
    private final static String LOG_TITLE = "Raw-Store";
    @Getter
    @Setter
    private int maxDepth = 64;


    private final static Resource DEFAULT_AVATAR = new ClassPathResource("/static/defaultAvatar.png");

    protected final DirectRawStoreHandler handler;
    protected final CopyAndMoveHandler copyAndMoveHandler;
    protected FileResourceMd5Resolver md5Resolver;

    private volatile StoreService uniqueStoreService;
    private volatile TempStoreService tempStoreService;

    public AbstractRawStoreService(DirectRawStoreHandler handler,
                                   FileResourceMd5Resolver md5Resolver
    ) {
        Assert.notNull(handler, "未能获取到存储操作器");
        this.handler = handler;
        copyAndMoveHandler = new CopyAndMoveHandlerImpl(handler, isMoveWithRecursion());
        this.md5Resolver = md5Resolver;

    }

    /**
     * 文件移动是否需要递归执行。
     * @return 若返回值为true，则通过递归逐个移动文件。若为false，则直接调用{@link CopyAndMoveHandler#moveFile(String, String)}方法进行移动。
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
        private final DirectRawStoreHandler handler;
        private final boolean moveWithRecursion;
        public CopyAndMoveHandlerImpl(DirectRawStoreHandler handler, boolean moveWithRecursion) {
            super(handler);
            this.handler = handler;
            this.moveWithRecursion = moveWithRecursion;
        }

        @Override
        protected boolean isMoveWithRecursion() {
            return moveWithRecursion;
        }

        @Override
        protected boolean copyFile(String src, String dest) throws IOException {
            return handler.copy(src, dest);
        }

        @Override
        protected boolean moveFile(String src, String dest) throws IOException {
            return handler.move(src, dest);
        }

        @Override
        protected boolean mkdir(String path) throws IOException {
            return handler.mkdirs(path);
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

    public final String getUserProfileRoot(int uid) {
        return StringUtils.appendPath(getStoreRoot(), "user_profile", uid + "");
    }

    public final String getUserFileRoot(int uid) {
        if (uid == User.PUBLIC_USER_ID) {
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
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {
        final String root = getUserFileRoot(uid);
        final String savePath = StringUtils.appendPath(root, targetDir, fileInfo.getName());
        handler.store(savePath, fileInfo.getSize(), input);
    }

    @Override
    public void rename(int uid, String path, String oldName, String newName) throws IOException {
        final String src = getPath(uid, path, oldName);
        handler.rename(src, newName);
    }

    @Override
    public boolean mkdir(int uid, String path, String name) throws IOException {
        return handler.mkdirs(getPath(uid, path, name));
    }

    @Override
    public long delete(int uid, String path, Collection<String> files) throws IOException {
        final String root = getPath(uid, path);
        for (String file : files) {
            if(!FileNameValidator.valid(file)) {
                throw new JsonException(CommonError.NOT_ALLOW_PATH);
            }
            handler.delete(StringUtils.appendPath(root, file));
        }
        return files.size();
    }

    @Override
    public Resource getAvatar(int uid) throws IOException {
        final String r = getUserProfileRoot(uid);
        for (FileInfo info : handler.listFiles(r)) {
            if (info.getName().startsWith("avatar.")) {
                return handler.getResource(StringUtils.appendPath(r, info.getName()));
            }
        }
        return null;
    }


    @Override
    public void saveAvatar(int uid, Resource resource) throws IOException {
        FileValidator.validateAvatar(resource);
        final String userProfileRoot = getUserProfileRoot(uid);
        for (FileInfo fileInfo : handler.listFiles(userProfileRoot)) {
            if (fileInfo.getName().startsWith("avatar.")) {
                handler.delete(StringUtils.appendPath(userProfileRoot, fileInfo.getName()));
            }
        }
        try(final InputStream is = resource.getInputStream()) {
            final String path = StringUtils.appendPath(userProfileRoot, "avatar." + FileUtils.getSuffix(resource.getFilename()));
            handler.store(path, resource.contentLength() ,is);
        }

    }

    @Override
    public Resource getDefaultAvatar() throws IOException {
        return DEFAULT_AVATAR;
    }

    /**
     * 获取指定UID用户的资源路径，将以用户的存储根为节点，往后拼接路径。
     * 安全保证：不会越出用户的存储根节点外
     * @param uid   用户ID，可以是公共网盘用户ID
     * @param path  路径节点集合
     * @return 资源完整存储路径
     */
    private String getPath(int uid, String...path) {
        String root = (uid == User.PUBLIC_USER_ID ?
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
    public List<FileInfo> lists(int uid, String path) throws IOException {
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
    public Resource getResource(int uid, String path, String name) throws IOException {
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
    public boolean exist(int uid, String path) throws IOException {
        return handler.exist(getPath(uid, path));
    }

    @Override
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        try(final InputStream is = Files.newInputStream(nativePath)) {
            final FileInfo fileResource = FileInfo.getFromResource(new PathResource(nativePath), uid, fileInfo.getType());
            fileResource.setName(fileInfo.getName());
            store(uid, is, diskPath, fileResource);
            is.close();
            Files.delete(nativePath);
        }
    }

    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, boolean overwrite) throws IOException {
        final String src = StringUtils.appendPath(getUserFileRoot(uid), source, sourceName);
        final String dst = StringUtils.appendPath(getUserFileRoot(targetId), target, targetName);
        copyAndMoveHandler.copy(src, dst, overwrite);

    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        final String src = StringUtils.appendPath(getUserFileRoot(uid), source, name);
        final String dst = StringUtils.appendPath(getUserFileRoot(uid), target, name);
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
    public TempStoreService getTempFileHandler() {
        // 双重校验锁懒汉单例
        if (tempStoreService != null) {
            return tempStoreService;
        }
        synchronized (this) {
            if (tempStoreService != null) {
                return tempStoreService;
            }
            String tempRoot = getTempRoot();
            tempStoreService = new DefaultTempStoreService(handler, tempRoot);
        }
        return tempStoreService;
    }

    @Override
    public List<FileSystemStatus> getStatus() {
        return Collections.emptyList();
    }
}
