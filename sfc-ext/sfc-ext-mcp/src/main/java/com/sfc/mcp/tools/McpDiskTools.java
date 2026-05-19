package com.sfc.mcp.tools;

import com.sfc.mcp.model.McpFileEntry;
import com.sfc.mcp.model.McpFileListResult;
import com.sfc.mcp.model.McpOperationResult;
import com.sfc.mcp.service.McpUploadService;
import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.vo.UserVO;
import com.xiaotao.saltedfishcloud.service.resource.FileLinkService;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.RequestContextUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.validator.ValidPathValidator;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * MCP 网盘工具服务。
 * <p>
 * 该服务通过 Spring AI 的 {@link Tool} 注解向 MCP Server 暴露网盘工具，
 * 并复用系统现有的 OAuth + Spring Security 上下文与网盘文件系统能力。
 * 所有工具方法均要求当前请求已通过 OAuth ApiTicket 认证，并拥有 {@link SysRole#OAUTH_USER} 角色。
 * </p>
 */
@RequiredArgsConstructor
@RolesAllowed(SysRole.OAUTH_USER)
public class McpDiskTools {

    private final DiskFileSystemManager diskFileSystemManager;
    private final UserService userService;
    private final McpUploadService mcpUploadService;
    private final FileLinkService fileLinkService;

    /**
     * 获取当前 OAuth / ApiTicket 对应用户的信息。
     *
     * @return 当前用户信息
     */
    @McpTool(name = "get_current_user_info", description = "获取当前 OAuth ApiTicket 对应的用户信息")
    public UserVO getCurrentUserInfo() {
        long currentUid = requireCurrentUid();
        return toUserVo(currentUid);
    }

    /**
     * 获取指定用户 ID 的用户信息。
     * <p>
     * 仅允许查询当前用户自己，或管理员查询任意用户。
     * </p>
     *
     * @param uid 用户 ID
     * @return 用户信息
     */
    @McpTool(name = "get_user_info", description = "获取指定用户 ID 的用户信息，仅允许查询当前用户自己，或管理员查询任意用户")
    public UserVO getUserInfo(
            @McpToolParam(description = "要查询的用户 ID") String uid
    ) {
        long uidValue = parseUid(uid);
        UIDValidator.validateWithException(uidValue, true);
        return toUserVo(uidValue);
    }

    /**
     * 获取指定网盘目录下的文件列表。
     *
     * @param uid  资源所属用户 ID。0 表示公共网盘，正整数表示私人网盘
     * @param path 目录路径，以 / 开头
     * @return 文件列表结果
     * @throws IOException 文件系统访问异常
     */
    @McpTool(name = "list_files", description = "获取公共网盘或私人网盘指定目录下的文件和文件夹列表")
    public McpFileListResult listFiles(
            @McpToolParam(description = "资源所属用户 ID，0 表示公共网盘，正整数表示私人网盘") String uid,
            @McpToolParam(description = "目录路径，以 / 开头，例如 / 或 /文档") String path
    ) throws IOException {
        long uidValue = parseUid(uid);
        UIDValidator.validateWithException(uidValue, false);
        List<FileInfo>[] fileList = diskFileSystemManager.getMainFileSystem().getUserFileList(uidValue, path);
        List<McpFileEntry> dirs = fileList == null ? Collections.emptyList() : toEntries(fileList[0]);
        List<McpFileEntry> files = fileList == null ? Collections.emptyList() : toEntries(fileList[1]);
        return new McpFileListResult(uidValue, path, dirs, files);
    }

    /**
     * 删除指定目录下的文件或文件夹。
     *
     * @param uid   资源所属用户 ID。0 表示公共网盘，仅管理员允许写入
     * @param path  文件所在目录路径
     * @param names 要删除的文件或文件夹名称列表
     * @return 操作结果
     * @throws IOException 文件系统访问异常
     */
    @McpTool(name = "delete_files", description = "删除公共网盘或私人网盘指定目录下的文件或文件夹")
    public McpOperationResult deleteFiles(
            @McpToolParam(description = "资源所属用户 ID，0 表示公共网盘，仅管理员允许写入") String uid,
            @McpToolParam(description = "文件所在目录路径，以 / 开头") String path,
            @McpToolParam(description = "待删除的文件或目录名称列表") List<String> names
    ) throws IOException {
        long uidValue = parseUid(uid);
        UIDValidator.validateWithException(uidValue, true);
        long deleted = diskFileSystemManager.getMainFileSystem().deleteFile(uidValue, path, names);
        return new McpOperationResult(true, "删除完成", deleted);
    }

    /**
     * 复制文件到目标目录。
     *
     * @param sourceUid   源资源所属用户 ID
     * @param sourcePath  源目录路径
     * @param files       要复制的文件名列表，传空则复制源目录下所有文件
     * @param targetUid   目标资源所属用户 ID
     * @param targetPath  目标目录路径
     * @param overwrite   是否允许覆盖同名文件
     * @return 操作结果
     * @throws IOException 文件系统访问异常
     */
    @McpTool(name = "copy_files", description = "复制文件，支持公共网盘与私人网盘之间的跨用户复制")
    public McpOperationResult copyFiles(
            @McpToolParam(description = "源资源所属用户 ID，0 表示公共网盘") String sourceUid,
            @McpToolParam(description = "源目录路径，以 / 开头") String sourcePath,
            @McpToolParam(required = false, description = "待复制文件名列表；为空时复制源目录下所有文件") List<String> files,
            @McpToolParam(description = "目标资源所属用户 ID，0 表示公共网盘") String targetUid,
            @McpToolParam(description = "目标目录路径，以 / 开头") String targetPath,
            @McpToolParam(required = false, description = "是否覆盖同名文件，默认 false") Boolean overwrite
    ) throws IOException {
        long sourceUidValue = parseUid(sourceUid);
        long targetUidValue = parseUid(targetUid);
        UIDValidator.validateWithException(sourceUidValue, false);
        UIDValidator.validateWithException(targetUidValue, true);
        SimpleFileTransferParam param = SimpleFileTransferParam.builder()
                .sourceUid(sourceUidValue)
                .sourcePath(sourcePath)
                .files(files)
                .targetUid(targetUidValue)
                .targetPath(targetPath)
                .isOverwrite(Boolean.TRUE.equals(overwrite))
                .build();
        diskFileSystemManager.getMainFileSystem().copy(param, null);
        return new McpOperationResult(true, "复制完成", files == null ? 0L : (long) files.size());
    }

    /**
     * 移动指定目录下的文件或目录到目标目录。
     *
     * @param uid       资源所属用户 ID
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @param names     要移动的文件或目录名称列表
     * @param overwrite 是否允许覆盖同名文件
     * @return 操作结果
     * @throws IOException 文件系统访问异常
     */
    @McpTool(name = "move_files", description = "在同一用户网盘内移动文件或目录")
    public McpOperationResult moveFiles(
            @McpToolParam(description = "资源所属用户 ID，0 表示公共网盘，仅管理员允许写入") String uid,
            @McpToolParam(description = "源目录路径，以 / 开头") String sourcePath,
            @McpToolParam(description = "目标目录路径，以 / 开头") String targetPath,
            @McpToolParam(description = "待移动的文件或目录名称列表") List<String> names,
            @McpToolParam(required = false, description = "是否覆盖同名文件，默认 false") Boolean overwrite
    ) throws IOException {
        long uidValue = parseUid(uid);
        UIDValidator.validateWithException(uidValue, true);
        boolean overwriteValue = Boolean.TRUE.equals(overwrite);
        for (String name : names) {
            diskFileSystemManager.getMainFileSystem().move(uidValue, sourcePath, targetPath, name, overwriteValue);
        }
        return new McpOperationResult(true, "移动完成", (long) names.size());
    }

    /**
     * 让 MCP 客户端获知上传文件到咸鱼云的方法
     */
    @McpTool(name = "get_upload_file_method", description = "获取上传文件到咸鱼云网盘的方法")
    public McpOperationResult getUploadFileMethod() {
        HttpServletRequest request = RequestContextUtils.getHttpServletRequest().orElseThrow();
        return new McpOperationResult(true, mcpUploadService.getUploadPrompt(request), 0L);
    }

    /**
     * 创建网盘文件的临时下载链接。
     *
     * @param uid  资源所属用户 ID
     * @param path 文件完整路径（包含文件名）
     * @return 操作结果，message 字段为生成的下载链接
     */
    @McpTool(name = "create_file_download_link", description = "创建网盘文件临时下载链接")
    public McpOperationResult createFileDownloadLink(
            @McpToolParam(description = "资源所属用户 ID，0 表示公共网盘，正整数表示私人网盘") String uid,
            @McpToolParam(description = "文件完整路径（包含文件名），以 / 开头") String path
    ) {
        long uidValue = parseUid(uid);
        UIDValidator.validateWithException(uidValue, false);
        ValidPathValidator.valid(path);

        String fileName = PathUtils.getLastNode(path);
        if (!StringUtils.hasText(fileName)) {
            throw new JsonException(CommonError.FORMAT_ERROR, "path必须包含文件名");
        }

        String parentPath = PathUtils.getParentPath(path);
        ResourceRequest resourceRequest = ResourceRequest.builder()
                .protocol(ResourceProtocol.MAIN)
                .targetId(String.valueOf(uidValue))
                .path(parentPath)
                .name(fileName)
                .build();
        HttpServletRequest request = RequestContextUtils.getHttpServletRequest().orElseThrow();
        String baseUrl = UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath("/api/fileLink/download")
                .replaceQuery(null)
                .build()
                .toUriString();
        String link = fileLinkService.createLink(baseUrl, resourceRequest);
        return new McpOperationResult(true, link, 1L);
    }

    /**
     * 获取通过开放平台下载文件的方法说明。
     *
     * @return 操作结果，message 字段为下载方法说明
     */
    @McpTool(name = "get_download_file_method", description = "获取通过开放平台下载文件的方法说明")
    public McpOperationResult getDownloadFileMethod() {
        HttpServletRequest request = RequestContextUtils.getHttpServletRequest().orElseThrow();
        String downloadApi = UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath("/api/openApi/diskFile/download/v1")
                .replaceQuery(null)
                .build()
                .toUriString();
        String prompt = "通过开放平台下载文件请使用 GET " + downloadApi + "，并传递 query 参数 uid 与 path。\n"
                + "Authorization 请求头需携带 ApiTicket access_token，且 token 需具备 storage_read 权限。\n"
                + "示例：curl -X GET \"" + downloadApi + "?uid=0&path=/document.pdf\" -H \"Authorization: ApiTicket YOUR_ACCESS_TOKEN\" -o document.pdf\n"
                + "说明：path 必须以 / 开头且为有效路径；uid=0 表示公共网盘，uid>0 表示私人网盘。";
        return new McpOperationResult(true, prompt, 0L);
    }

    /**
     * 重命名文件或目录。
     *
     * @param uid     资源所属用户 ID
     * @param path    文件所在目录路径
     * @param oldName 原文件名或目录名
     * @param newName 新文件名或目录名
     * @return 操作结果
     * @throws IOException 文件系统访问异常
     */
    @McpTool(name = "rename_file", description = "重命名公共网盘或私人网盘中的文件或目录")
    public McpOperationResult renameFile(
            @McpToolParam(description = "资源所属用户 ID，0 表示公共网盘，仅管理员允许写入") String uid,
            @McpToolParam(description = "文件所在目录路径，以 / 开头") String path,
            @McpToolParam(description = "原文件名或目录名") String oldName,
            @McpToolParam(description = "新文件名或目录名") String newName
    ) throws IOException {
        long uidValue = parseUid(uid);
        UIDValidator.validateWithException(uidValue, true);
        diskFileSystemManager.getMainFileSystem().rename(uidValue, path, oldName, newName);
        return new McpOperationResult(true, "重命名完成", 1L);
    }

    /**
     * 创建目录。
     *
     * @param uid  资源所属用户 ID
     * @param path 要创建的完整目录路径
     * @return 操作结果
     * @throws IOException 文件系统访问异常
     */
    @McpTool(name = "create_directory", description = "在公共网盘或私人网盘中递归创建目录")
    public McpOperationResult createDirectory(
            @McpToolParam(description = "资源所属用户 ID，0 表示公共网盘，仅管理员允许写入") String uid,
            @McpToolParam(description = "要创建的完整目录路径，以 / 开头，例如 /新目录 或 /文档/归档") String path
    ) throws IOException {
        long uidValue = parseUid(uid);
        UIDValidator.validateWithException(uidValue, true);
        diskFileSystemManager.getMainFileSystem().mkdirs(uidValue, path);
        return new McpOperationResult(true, "目录创建完成", 1L);
    }

    /**
     * 解析并转换 UID 字符串。
     *
     * @param uid UID 字符串
     * @return UID 数值
     */
    private long parseUid(String uid) {
        try {
            return Long.parseLong(uid);
        } catch (NumberFormatException ex) {
            throw new JsonException(CommonError.FORMAT_ERROR);
        }
    }

    /**
     * 将文件信息列表转换为 MCP 文件条目列表。
     *
     * @param fileInfos 文件信息列表
     * @return MCP 文件条目列表
     */
    private List<McpFileEntry> toEntries(List<FileInfo> fileInfos) {
        List<McpFileEntry> entries = new ArrayList<>();
        for (FileInfo fileInfo : Optional.ofNullable(fileInfos).orElse(Collections.emptyList())) {
            String md5 = fileInfo.isDir() ? null : fileInfo.getMd5();
            entries.add(new McpFileEntry(fileInfo.getName(), fileInfo.getSize(), fileInfo.getMtime(), fileInfo.isDir(), md5));
        }
        return entries;
    }

    /**
     * 根据文件保存结果码生成说明文本。
     *
     * @param saveResult 文件保存结果码
     * @return 结果说明
     */
    private String resolveSaveMessage(long saveResult) {
        if (saveResult == 0L) {
            return "上传完成，已覆盖同名文件";
        }
        if (saveResult == 2L) {
            return "上传完成，文件内容无变化";
        }
        return "上传完成，已创建新文件";
    }

    /**
     * 获取当前认证用户 ID。
     *
     * @return 当前用户 ID
     */
    private long requireCurrentUid() {
        Long currentUidValue = SecureUtils.getCurrentUid();
        if (currentUidValue == null) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
        return currentUidValue;
    }

    /**
     * 将指定用户 ID 转换为脱敏的用户视图对象。
     *
     * @param uid 用户 ID
     * @return 用户视图对象
     */
    private UserVO toUserVo(long uid) {
        User user = userService.getUserById(uid);
        if (user == null) {
            throw new UserNoExistException();
        }
        return UserVO.from(user, true);
    }
}

