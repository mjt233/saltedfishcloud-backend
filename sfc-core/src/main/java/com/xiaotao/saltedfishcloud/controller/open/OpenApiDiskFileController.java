package com.xiaotao.saltedfishcloud.controller.open;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.constant.StandardScopes;
import com.sfc.common.constant.SfcSysRole;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.sfc.common.exception.SfcJsonException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.sfc.common.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.model.param.FileNameList;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.FileLinkService;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.sfc.common.utils.SfcStringUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.xiaotao.saltedfishcloud.validator.ValidPathValidator;
import com.sfc.cloud.data.validation.SfcUID;
import com.xiaotao.saltedfishcloud.validator.annotations.ValidPath;
import com.xiaotao.saltedfishcloud.annotations.RequireScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 开放平台网盘文件操作 API Controller。
 * <p>
 * 提供对指定 SfcUID 网盘（公共或私人）的文件列表查询、上传、复制、移动、重命名和删除功能。
 * 所有接口均要求 OAuth 用户已授权相应的 {@code storage_read} 或 {@code storage_write} 权限范围。
 * </p>
 */
@RestController
@RolesAllowed(SfcSysRole.OAUTH_USER)
@RequestMapping("/api/openApi/diskFile")
@RequiredArgsConstructor
@Validated
@Tag(name = "开放平台 - 网盘文件操作")
public class OpenApiDiskFileController {

    private final DiskFileSystemManager diskFileSystemManager;

    /**
     * 文件临时链接服务。
     */
    private final FileLinkService fileLinkService;

    /**
     * 获取用户网盘指定目录下的文件列表。
     *
     * @param uid  用户ID（0 为公共网盘）
     * @param path 目录路径，以 "/" 开头，默认为根目录
     * @return 目录与文件的合并列表（目录项在前，文件项在后）
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "获取网盘文件列表")
    @GetMapping("/fileList/v1")
    @RequireScope(StandardScopes.STORAGE_READ)
    public SfcJsonResult<List<FileInfo>> getFileList(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @SfcUID long uid,
            @Parameter(description = "目录路径，默认为根目录 /", example = "/")
            @RequestParam(value = "path", defaultValue = "/") @ValidPath String path) throws IOException {
        DiskFileSystem fs = diskFileSystemManager.getMainFileSystem();
        Collection<? extends FileInfo>[] fileList = fs.getUserFileList(uid, path);
        List<FileInfo> mergedList = new ArrayList<>();
        if (fileList != null) {
            for (Collection<? extends FileInfo> group : fileList) {
                if (group != null) {
                    mergedList.addAll(group);
                }
            }
        }
        return SfcJsonResult.getInstance(mergedList);
    }

    /**
     * 下载用户网盘指定路径的文件。
     *
     * @param uid  用户ID（0 为公共网盘）
     * @param path 文件完整路径（包含文件名），以 "/" 开头
     * @return HTTP 响应，包含文件内容和相应的响应头
     * @throws IOException              文件系统访问异常
     * @throws UnsupportedEncodingException 文件名编码异常
     */
    @Operation(summary = "下载网盘文件")
    @GetMapping("/download/v1")
    @RequireScope(StandardScopes.STORAGE_READ)
    public ResponseEntity<Resource> download(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @SfcUID long uid,
            @Parameter(description = "文件完整路径（包含文件名）", required = true)
            @RequestParam("path") @ValidPath String path) throws IOException, UnsupportedEncodingException {
        // 从完整路径中提取目录路径和文件名
        String parentPath = PathUtils.getParentPath(path);
        String fileName = PathUtils.getLastNode(path);

        // 获取文件资源
        DiskFileSystem fs = diskFileSystemManager.getMainFileSystem();
        Resource resource = fs.getResource(uid, parentPath, fileName);

        if (resource == null) {
            throw new SfcJsonException(FileSystemError.FILE_NOT_FOUND);
        }

        return ResourceUtils.wrapResource(resource, fileName);
    }

    /**
     * 创建网盘文件的临时下载链接。
     *
     * @param uid     用户ID（0 为公共网盘）
     * @param path    文件完整路径（包含文件名）
     * @param request HTTP 请求对象
     * @return 临时下载链接
     */
    @Operation(summary = "创建网盘文件下载链接")
    @GetMapping("/downloadLink/v1")
    @RequireScope(StandardScopes.STORAGE_READ)
    public SfcJsonResult<String> createDownloadLink(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @SfcUID long uid,
            @Parameter(description = "文件完整路径（包含文件名）", required = true)
            @RequestParam("path") @ValidPath String path,
            HttpServletRequest request) {
        String parentPath = PathUtils.getParentPath(path);
        String fileName = PathUtils.getLastNode(path);
        ResourceRequest resourceRequest = ResourceRequest.builder()
                .protocol(ResourceProtocol.MAIN)
                .targetId(String.valueOf(uid))
                .path(parentPath)
                .name(fileName)
                .build();

        String baseUrl = UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath("/api/fileLink/download")
                .replaceQuery(null)
                .build()
                .toUriString();
        return SfcJsonResult.getInstance(fileLinkService.createLink(baseUrl, resourceRequest));
    }

    /**
     * 上传文件到用户网盘指定目录。
     *
     * @param uid   用户ID（0 为公共网盘）
     * @param path  文件保存的目标目录路径，以 "/" 开头
     * @param file  上传的文件内容
     * @return 操作结果码（0-覆盖，1-新文件，2-无变化）
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "上传文件到网盘")
    @PostMapping("/upload/v1")
    @RequireScope(StandardScopes.STORAGE_WRITE)
    public SfcJsonResult<Long> upload(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @SfcUID(true) long uid,
            @Parameter(description = "目标目录路径", required = true)
            @RequestParam("path") String path,
            @Parameter(description = "上传的文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new SfcJsonException(400, "文件为空");
        }
        if (!ValidPathValidator.isValid(path)) {
            throw new SfcJsonException(FileSystemError.INVALID_PATH, "当前值：" + path);
        }
        if (SfcStringUtils.hasText(file.getName()) && !FileNameValidator.valid(file.getName())) {
            throw new SfcJsonException(FileSystemError.INVALID_FILE_NAME, "当前值：" + file.getName());
        }
        FileInfo fileInfo = new FileInfo(file);
        fileInfo.setUid(uid);
        long result = diskFileSystemManager.getMainFileSystem().saveFile(fileInfo, path);
        return SfcJsonResult.getInstance(result);
    }

    /**
     * 创建目录。
     *
     * @param uid  用户ID（0 为公共网盘）
     * @param path 父目录路径，以 "/" 开头
     * @param name 新目录名称
     * @return 空成功响应
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "创建目录")
    @PostMapping("/mkdir/v1")
    @RequireScope(StandardScopes.STORAGE_WRITE)
    public SfcJsonResult<Object> mkdir(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @SfcUID(true) long uid,
            @Parameter(description = "父目录路径", required = true)
            @RequestParam("path") @ValidPath String path,
            @Parameter(description = "新目录名称", required = true)
            @RequestParam("name") String name) throws IOException {
        diskFileSystemManager.getMainFileSystem().mkdir(uid, path, name);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 复制文件或目录到目标目录（支持跨用户网盘）。
     * <p>
     * sourceUid 和 targetUid 优先从请求体 param 中获取，
     * 若未指定则回退到 QueryParam 的 uid（仅作兼容使用）。
     * </p>
     *
     * @param uid   用户ID（兼容参数，优先使用 param.sourceUid / param.targetUid）
     * @param param 复制参数，包括源路径、待复制文件列表、目标路径及可选的 sourceUid/targetUid
     * @return 空成功响应
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "复制文件或目录（支持跨用户网盘）")
    @PostMapping("/copy/v1")
    @RequireScope(StandardScopes.STORAGE_WRITE)
    public SfcJsonResult<Object> copy(
            @Parameter(description = "用户ID，兼容参数，优先使用请求体中 sourceUid 和 targetUid")
            @RequestParam("uid") Long uid,
            @RequestBody @Validated OpenApiFileTransferParam param) throws IOException {
        ResolvedTransferUid resolved = resolveTransferUids(uid, param);
        SfcUIDValidator.validateWithException(resolved.getSourceUid(), false);
        SfcUIDValidator.validateWithException(resolved.getTargetUid(), true);
        SimpleFileTransferParam transferParam = SimpleFileTransferParam.builder()
                .sourceUid(resolved.getSourceUid())
                .sourcePath(param.getSourcePath())
                .files(param.getFiles())
                .targetUid(resolved.getTargetUid())
                .targetPath(param.getTargetPath())
                .isOverwrite(param.getIsOverwrite() != null && param.getIsOverwrite())
                .build();
        diskFileSystemManager.getMainFileSystem().copy(transferParam, null);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 移动文件或目录到目标目录（支持跨用户网盘）。
     * <p>
     * sourceUid 和 targetUid 优先从请求体 param 中获取，
     * 若未指定则回退到 QueryParam 的 uid（仅作兼容使用）。
     * </p>
     *
     * @param uid   用户ID（兼容参数，优先使用 param.sourceUid / param.targetUid）
     * @param param 移动参数，包括源目录、文件名列表、目标目录及可选的 sourceUid/targetUid
     * @return 空成功响应
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "移动文件或目录（支持跨用户网盘）")
    @PostMapping("/move/v1")
    @RequireScope(StandardScopes.STORAGE_WRITE)
    public SfcJsonResult<Object> move(
            @Parameter(description = "用户ID，兼容参数，优先使用请求体中 sourceUid 和 targetUid")
            @RequestParam("uid") Long uid,
            @RequestBody @Validated OpenApiFileTransferParam param) throws IOException {
        ResolvedTransferUid resolved = resolveTransferUids(uid, param);
        SfcUIDValidator.validateWithException(resolved.getSourceUid(), true);
        SfcUIDValidator.validateWithException(resolved.getTargetUid(), true);
        DiskFileSystem fs = diskFileSystemManager.getMainFileSystem();
        boolean overwrite = param.getIsOverwrite() != null && param.getIsOverwrite();
        for (String name : param.getFiles()) {
            fs.move(resolved.getSourceUid(), param.getSourcePath(), resolved.getTargetUid(), param.getTargetPath(), name, overwrite);
        }
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 重命名文件或目录。
     *
     * @param uid     用户ID（0 为公共网盘）
     * @param path    文件或目录所在的父目录路径
     * @param oldName 原文件名
     * @param newName 新文件名
     * @return 空成功响应
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "重命名文件或目录")
    @PostMapping("/rename/v1")
    @RequireScope(StandardScopes.STORAGE_WRITE)
    public SfcJsonResult<Object> rename(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @SfcUID(true) long uid,
            @Parameter(description = "文件所在目录路径", required = true)
            @RequestParam("path") @ValidPath String path,
            @Parameter(description = "原文件名", required = true)
            @RequestParam("oldName") String oldName,
            @Parameter(description = "新文件名", required = true)
            @RequestParam("newName") String newName) throws IOException {
        if (newName == null || newName.trim().isEmpty()) {
            throw new SfcJsonException(400, "文件名不能为空");
        }
        diskFileSystemManager.getMainFileSystem().rename(uid, path, oldName, newName);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 删除文件或目录。
     *
     * @param uid   用户ID（0 为公共网盘）
     * @param path  文件或目录所在的父目录路径
     * @param param 待删除的文件名列表
     * @return 实际删除的数量
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "删除文件或目录")
    @DeleteMapping("/delete/v1")
    @RequireScope(StandardScopes.STORAGE_WRITE)
    public SfcJsonResult<Object> delete(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @SfcUID(true) long uid,
            @Parameter(description = "文件所在目录路径", required = true)
            @RequestParam("path") @ValidPath String path,
            @RequestBody @Validated FileNameList param) throws IOException {
        diskFileSystemManager.getMainFileSystem().deleteFile(uid, path, param.getFileName());
        return SfcJsonResult.emptySuccess();
    }

    // ========================= Inner DTO =========================

    /**
     * 开放 API 文件传输参数（支持跨用户网盘）。
     * <p>
     * 用于 copy 和 move 操作。优先使用 sourceUid 和 targetUid 分别指定源和目标用户，
     * 若未指定则回退到 QueryParam 的 uid。
     * </p>
     */
    @Data
    public static class OpenApiFileTransferParam {

        /**
         * 源用户ID，优先使用；不传则使用 QueryParam 的 uid
         */
        private Long sourceUid;

        /**
         * 源文件所在目录路径
         */
        private String sourcePath;

        /**
         * 目标用户ID，优先使用；不传则使用 QueryParam 的 uid
         */
        private Long targetUid;

        /**
         * 待传输的文件名列表，为 null 则操作 sourcePath 下的所有文件
         */
        private List<String> files;

        /**
         * 目标目录路径
         */
        private String targetPath;

        /**
         * 是否覆盖同名文件
         */
        private Boolean isOverwrite;
    }

    /**
     * 解析后的文件传输 SfcUID 结果。
     */
    @AllArgsConstructor
    @Data
    private static class ResolvedTransferUid {
        private long sourceUid;
        private long targetUid;
    }

    /**
     * 从请求参数中解析源和目标用户ID。
     * <p>
     * sourceUid 和 targetUid 优先从请求体 param 中获取，
     * 若未指定则回退到 QueryParam 的 uid。
     * </p>
     *
     * @param uid   QueryParam 的兼容用户ID
     * @param param 文件传输参数
     * @return 解析后的 sourceUid 和 targetUid
     * @throws SfcJsonException 缺少必要参数时抛出
     */
    private ResolvedTransferUid resolveTransferUids(Long uid, OpenApiFileTransferParam param) {
        long sourceUid;
        if (param.getSourceUid() != null) {
            sourceUid = param.getSourceUid();
        } else if (uid != null) {
            sourceUid = uid;
        } else {
            throw new SfcJsonException(400, "缺少 sourceUid 参数，请通过请求体 sourceUid 字段或 QueryParam uid 指定");
        }

        long targetUid;
        if (param.getTargetUid() != null) {
            targetUid = param.getTargetUid();
        } else if (uid != null) {
            targetUid = uid;
        } else {
            throw new SfcJsonException(400, "缺少 targetUid 参数，请通过请求体 targetUid 字段或 QueryParam uid 指定");
        }

        return new ResolvedTransferUid(sourceUid, targetUid);
    }
}
