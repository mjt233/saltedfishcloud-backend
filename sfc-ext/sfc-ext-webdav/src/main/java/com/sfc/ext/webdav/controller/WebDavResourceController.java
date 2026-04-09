package com.sfc.ext.webdav.controller;

import com.sfc.ext.webdav.enums.ResourceArea;
import com.sfc.ext.webdav.model.resource.*;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.*;
import io.milton.annotations.*;
import io.milton.http.ExistingEntityHandler;
import io.milton.http.HttpManager;
import io.milton.http.Request;
import io.milton.http.Response;
import io.milton.http.exceptions.BadRequestException;
import io.milton.resource.Resource;
import io.milton.servlet.MiltonServlet;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.util.Lazy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

import static com.sfc.ext.webdav.enums.ResourceArea.PRIVATE;
import static com.sfc.ext.webdav.enums.ResourceArea.PUBLIC;

@Slf4j
@ResourceController
public class WebDavResourceController {
    private final static String LOG_PREFIX = "[WebDAV资源]";
    private final Lazy<UserService> userServiceLazy = Lazy.of(() -> SpringContextUtils.getContext().getBean(UserService.class));
    private final Lazy<DiskFileSystemManager> diskFileSystemManagerLazy = Lazy.of(() -> SpringContextUtils.getContext().getBean(DiskFileSystemManager.class));


    @Root
    public WebDavRoot getRoot() {
        User curUser = getCurUser();
        if (curUser == null) {
            return new UnAuthoriseWebDavRoot();
        }
        return new WebDavRoot(curUser.getId());
    }


    @ChildrenOf
    public List<WebDavItem> getRootList(WebDavRoot root) {
        Date date = new Date();
        List<WebDavItem> list = new ArrayList<>();
        list.add(WebDavDir.builder()
                .name(PUBLIC.getName())
                .createDate(date)
                .modifiedDate(date)
                .resourceArea(PUBLIC)
                .uid(User.PUBLIC_USER_ID)
                .isVirtualRoot(true)
                .path("/")
                .build());

        list.add(WebDavDir.builder()
                .name(PRIVATE.getName())
                .createDate(date)
                .modifiedDate(date)
                .resourceArea(ResourceArea.PRIVATE)
                .isVirtualRoot(true)
                .uid(
                        Optional.ofNullable(root).map(WebDavRoot::getUid)
                                .or(() -> Optional.ofNullable(getCurUser()).map(User::getId))
                                .orElse(null)
                )
                .path("/")
                .build()
        );

        return list;
    }

    private List<WebDavItem> getDiskFileList(Long uid, String requirePath) throws IOException {
        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        List<FileInfo>[] userFileList = fileSystem.getUserFileList(uid, requirePath);
        return Stream.concat(
                        userFileList[0].stream(),
                        userFileList[1].stream()
                )
                .map(fileInfo -> WebDavItem.fromFileInfo(fileInfo, requirePath))
                .toList();
    }

    @ChildOf
    public WebDavItem findChild(WebDavDir parent, String name) {
        // 访问资源必须要求要有用户认证信息
        User user = getCurUser(parent.getUid());
        if (user == null) {
            return UnAuthoriseWebDavItem.get(parent, name);
        }
        return null;
    }

    @ChildOf
    public WebDavItem findChild(UnAuthoriseWebDavItem parent, String name) {
        return UnAuthoriseWebDavItem.get(parent, name);
    }

    @ChildrenOf
    public List<WebDavItem> getFileList(WebDavDir f) throws IOException {
        boolean isPublic = f.getResourceArea() == PUBLIC;
        String requirePath = f.getPath();
        try {
            Long uid;
            if (isPublic) {
                uid = User.PUBLIC_USER_ID;
            } else {
                User user = getCurUser(f.getUid());
                if (user == null) {
                    return Collections.singletonList(UnAuthoriseWebDavItem.get(f, ""));
                } else {
                    uid = user.getId();
                }
            }
            return getDiskFileList(uid, requirePath);
        } catch (Exception e) {
            log.error("{}uid: {} path: {} 获取文件列表失败", LOG_PREFIX, f.getUid(), requirePath, e);
            return Collections.singletonList(WebDavItem.fromError(e.getMessage(), f.getUid(), requirePath));
        }
    }

    @Get
    public InputStream getFile(WebDavFile file) throws IOException {
        if (file.isError()) {
            return new ByteArrayInputStream(file.getErrorMessage().getBytes());
        }

        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        String parentPath = PathUtils.getParentPath(file.getPath());
        org.springframework.core.io.Resource fileResource = fileSystem.getResource(file.getUid(), parentPath, file.getName());
        if (fileResource == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND, file.getPath());
        }
        return fileResource.getInputStream();
    }

    /**
     * Milton在响应401未认证之前，会先检查资源控制器是否有兼容对应参数类型的GET方法，如果没有会直接响应501。<br>
     * 部分WebDAV客户端（如Sardine）在发起GET请求时可能不会携带认证信息，会先判断触发了401后自动再次请求才会携带认证信息。而Sardine检测到响应码是501而不是401时，会当作请求异常处理。
     * @see io.milton.http.ResourceHandlerHelper#processResource(HttpManager, Request, Response, Resource, ExistingEntityHandler, boolean, Map, Map) Milton库逻辑
     */
    @Get
    public InputStream getUnAuthoriseFile(UnAuthoriseWebDavItem item) {
        return new ByteArrayInputStream(new byte[0]);
    }

    @MakeCollection
    public WebDavItem mkdir(WebDavDir parent, String newName) throws IOException {
        long uid = parent.getResourceArea() == PUBLIC ? User.PUBLIC_USER_ID : getCurUser(parent.getUid()).getId();
        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        fileSystem.mkdir(uid, parent.getPath(), newName);
        
        return getFileItemFromPath(uid, parent.getPath(), newName);
    }

    @MakeCollection
    public WebDavItem rootMkdir(WebDavRoot root, String newName) {
        if (newName.equals(PUBLIC.getName()) || newName.equals(PRIVATE.getName())) {
            return getRootList(root).stream().filter(r -> r.getName().equals(PRIVATE.getName())).findAny().orElse(null);
        }
        return null;
    }

    @PutChild
    public WebDavItem upload(WebDavDir parent, String name, InputStream in, Long contentLength) throws IOException {
        long uid = parent.getResourceArea() == PUBLIC ? User.PUBLIC_USER_ID : getCurUser(parent.getUid()).getId();
        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setName(name);
        fileInfo.setPath(StringUtils.appendPath(parent.getPath(), name));
        fileInfo.setUid(uid);
        Optional.ofNullable(HttpManager.request().getHeaders().get("X-OC-Mtime"))
                .filter(StringUtils::hasText)
                .map(t -> new Date(TypeUtils.toLong(t) * 1000))
                .ifPresent(date -> {
                    fileInfo.setMtime(date.getTime());
                    fileInfo.setCtime(date.getTime());
                });

        fileSystem.saveFileByStream(fileInfo, parent.getPath(), os -> DiskFileSystemUtils.saveFileStream(fileInfo, in, os));
        return WebDavItem.fromFileInfo(fileInfo, parent.getPath());
    }

    /**
     * 重命名文件或目录
     */
    @Move
    public WebDavItem renameOrMove(WebDavItem source, WebDavItem newParent, String newName) throws IOException, BadRequestException {
        if (source.isVirtualRoot()) {
            throw new BadRequestException("Can not handle virtual directory");
        }
        String sourceParent = PathUtils.getParentPath(source.getPath());
        if (Objects.equals(sourceParent, newParent.getPath())) {
            // 父级目录相同，执行重命名
            return doRename(source, newParent, newName);
        } else {
            // 父级目录不同，执行移动
            return doMove(source, newParent, newName);
        }
    }

    public WebDavItem doRename(WebDavItem source, WebDavItem newParent, String newName) throws IOException {
        long uid = source.getResourceArea() == PUBLIC ? User.PUBLIC_USER_ID : getCurUser(source.getUid()).getId();
        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        String parentPath = PathUtils.getParentPath(source.getPath());
        fileSystem.rename(uid, parentPath, source.getName(), newName);
    
        return getFileItemFromPath(uid, parentPath, newName);
    }

    /**
     * 移动文件或目录
     */
    public WebDavItem doMove(WebDavItem source, WebDavItem newParent, String newName) throws IOException {
        long uid = source.getResourceArea() == PUBLIC ? User.PUBLIC_USER_ID : getCurUser(source.getUid()).getId();
        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        String sourcePath = PathUtils.getParentPath(source.getPath());
        String targetPath = newParent.getPath();

        String targetName = newName;
        
        if (!source.getName().equals(targetName)) {
            // 需要更改名称 - 先重命名，再移动
            String tempName = source.getName() + "_" + System.currentTimeMillis() + "_tmp";
            
            // 1. 先重命名源文件为临时名称
            fileSystem.rename(uid, sourcePath, source.getName(), tempName);
            
            try {
                // 2. 移动到目标位置（使用临时名称）
                fileSystem.move(uid, sourcePath, targetPath, tempName, true);
                
                // 3. 最后重命名为目标名称
                if (getFileItemFromPath(uid, targetName, tempName) != null) {
                    // 目标名称已存在，就用临时名称了
                    targetName = tempName;
                }
                
                fileSystem.rename(uid, targetPath, tempName, targetName);
            } catch (IOException e) {
                // 如果移动失败，需要恢复原名称
                fileSystem.rename(uid, sourcePath, tempName, source.getName());
                throw e;
            }
        } else {
            // 名称相同 - 直接移动
            fileSystem.move(uid, sourcePath, targetPath, targetName, true);
        }

        return getFileItemFromPath(uid, targetPath, targetName);
    }

    /**
     * 删除文件或目录
     */
    @Delete
    public void delete(WebDavItem item) throws IOException, BadRequestException {
        if (item.isVirtualRoot()) {
            throw new BadRequestException("Can not handle virtual directory");
        }
        long uid = item.getResourceArea() == PUBLIC ? User.PUBLIC_USER_ID : getCurUser(item.getUid()).getId();
        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        String parentPath = PathUtils.getParentPath(item.getPath());
        fileSystem.deleteFile(uid, parentPath, Collections.singletonList(item.getName()));
    }

    /**
     * 复制文件或目录
     */
    @Copy
    public void copy(WebDavItem source, WebDavItem newParent, String newName) throws IOException, BadRequestException {
        if (source.isVirtualRoot()) {
            throw new BadRequestException("Can not handle virtual directory");
        }
        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        String sourcePath = PathUtils.getParentPath(source.getPath());
        String targetPath = newParent.getPath();

        // 如果目标目录不是当前目录，需要先检查是否存在同名文件
        String targetName = (newName != null && !newName.isEmpty()) ? newName : source.getName();
        fileSystem.copy(SimpleFileTransferParam.builder()
                        .files(List.of(targetName))
                        .isOverwrite(true)
                        .sourceUid(source.getUid())
                        .sourcePath(sourcePath)
                        .targetUid(newParent.getUid())
                        .targetPath(targetPath)
                .build(), null);
    }


    /**
     * 获取指定路径下的文件信息
     * @param uid 用户id
     * @param parentPath 父级目录
     * @param fileName 文件名
     * @return 文件信息，若文件不存在，则返回null
     */
    private WebDavItem getFileItemFromPath(long uid, String parentPath, String fileName) throws IOException {
        DiskFileSystem fileSystem = diskFileSystemManagerLazy.get().getMainFileSystem();
        List<FileInfo> fileList = fileSystem.getUserFileList(uid, parentPath, Collections.singletonList(fileName));
        if (fileList == null || fileList.isEmpty()) {
            return null;
        }
        return WebDavItem.fromFileInfo(fileList.get(0), parentPath);
    }


    private User getCurUser() {
        return getCurUser(null);
    }

    /**
     * 获取当前登录的用户信息
     * @param uid   请求的资源的uid，如果无法根据请求获取用户对象时会根据该id查询用户信息
     */
    private User getCurUser(@Nullable Long uid) {
        HttpServletRequest servletRequest = MiltonServlet.request();

        // 1. 首先从request attribute获取
        Object userObj = servletRequest.getAttribute("userObj");
        if (userObj instanceof User) {
            return (User) userObj;
        }

        // 2. 从session获取（认证成功后会同步到session）
        userObj = Optional.ofNullable(servletRequest.getSession(false))
                .map(session -> session.getAttribute("userObj"))
                .filter(obj -> obj instanceof User)
                .orElse(null);
        if (userObj != null) {
            servletRequest.setAttribute("userObj", userObj);
            return (User) userObj;
        }

        // 3. 从Authorization获取
        User user = (User) Optional.of(HttpManager.request())
                .map(Request::getAuthorization)
                .map(authorization -> {
                    Object tag = authorization.getTag();
                    if (tag == null) {
                        tag = userServiceLazy.get().getUserByAccount(authorization.getUser());
                        servletRequest.setAttribute("userObj", tag);
                    }
                    return tag;
                })
                .filter(t -> t instanceof User)
                .orElse(null);

        // 4. 如果仍然没有且提供了uid，根据uid查询
        if (user == null && uid != null) {
            user = userServiceLazy.get().getUserById(uid);
            servletRequest.setAttribute("userObj", user);
        }
        if (user == null) {
            log.warn("{} 无法获取到当前登录用户", LOG_PREFIX);
        }
        return user;
    }
}