package com.xiaotao.saltedfishcloud.controller.open;

import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.FileNameList;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.FileLinkService;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import com.xiaotao.saltedfishcloud.validator.ValidPathValidator;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import com.xiaotao.saltedfishcloud.validator.annotations.ValidPath;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 开放平台网盘文件操作 API Controller。
 * <p>
 * 提供对指定 UID 网盘（公共或私人）的文件列表查询、上传、复制、移动、重命名和删除功能。
 * 所有接口均要求 OAuth 用户已授权相应的 {@code storage_read} 或 {@code storage_write} 权限范围。
 * </p>
 */
@RestController
@RolesAllowed(SysRole.OAUTH_USER)
@RequestMapping("/api/openApi/diskFile")
@RequiredArgsConstructor
@Validated
@Api(tags = "开放平台 - 网盘文件操作")
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
    @ApiOperation("获取网盘文件列表")
    @GetMapping("/fileList/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_read')")
    public JsonResult<List<FileInfo>> getFileList(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID long uid,
            @ApiParam(value = "目录路径，默认为根目录 /", defaultValue = "/")
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
        return JsonResultImpl.getInstance(mergedList);
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
    @ApiOperation("下载网盘文件")
    @GetMapping("/download/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_read')")
    public ResponseEntity<Resource> download(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID long uid,
            @ApiParam(value = "文件完整路径（包含文件名）", required = true)
            @RequestParam("path") @ValidPath String path) throws IOException, UnsupportedEncodingException {
        // 从完整路径中提取目录路径和文件名
        String parentPath = PathUtils.getParentPath(path);
        String fileName = PathUtils.getLastNode(path);

        // 获取文件资源
        DiskFileSystem fs = diskFileSystemManager.getMainFileSystem();
        Resource resource = fs.getResource(uid, parentPath, fileName);

        if (resource == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
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
    @ApiOperation("创建网盘文件下载链接")
    @GetMapping("/downloadLink/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_read')")
    public JsonResult<String> createDownloadLink(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID long uid,
            @ApiParam(value = "文件完整路径（包含文件名）", required = true)
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
        return JsonResultImpl.getInstance(fileLinkService.createLink(baseUrl, resourceRequest));
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
    @ApiOperation("上传文件到网盘")
    @PostMapping("/upload/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_write')")
    public JsonResult<Long> upload(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID(true) long uid,
            @ApiParam(value = "目标目录路径", required = true)
            @RequestParam("path") String path,
            @ApiParam(value = "上传的文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new JsonException(400, "文件为空");
        }
        if (!ValidPathValidator.isValid(path)) {
            throw new JsonException(FileSystemError.INVALID_PATH, "当前值：" + path);
        }
        if (StringUtils.hasText(file.getName()) && !FileNameValidator.valid(file.getName())) {
            throw new JsonException(FileSystemError.INVALID_FILE_NAME, "当前值：" + file.getName());
        }
        FileInfo fileInfo = new FileInfo(file);
        fileInfo.setUid(uid);
        long result = diskFileSystemManager.getMainFileSystem().saveFile(fileInfo, path);
        return JsonResultImpl.getInstance(result);
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
    @ApiOperation("创建目录")
    @PostMapping("/mkdir/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_write')")
    public JsonResult<Object> mkdir(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID(true) long uid,
            @ApiParam(value = "父目录路径", required = true)
            @RequestParam("path") @ValidPath String path,
            @ApiParam(value = "新目录名称", required = true)
            @RequestParam("name") String name) throws IOException {
        diskFileSystemManager.getMainFileSystem().mkdir(uid, path, name);
        return JsonResult.emptySuccess();
    }

    /**
     * 复制文件或目录到目标目录。
     *
     * @param uid   用户ID（0 为公共网盘）
     * @param param 复制参数，包括源路径、待复制文件列表及目标路径
     * @return 空成功响应
     * @throws IOException 文件系统访问异常
     */
    @ApiOperation("复制文件或目录")
    @PostMapping("/copy/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_write')")
    public JsonResult<Object> copy(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID(true) long uid,
            @RequestBody @Validated OpenApiCopyParam param) throws IOException {
        SimpleFileTransferParam transferParam = SimpleFileTransferParam.builder()
                .sourceUid(uid)
                .sourcePath(param.getSourcePath())
                .files(param.getFiles())
                .targetUid(uid)
                .targetPath(param.getTargetPath())
                .isOverwrite(param.getIsOverwrite() != null && param.getIsOverwrite())
                .build();
        diskFileSystemManager.getMainFileSystem().copy(transferParam, null);
        return JsonResult.emptySuccess();
    }

    /**
     * 移动文件或目录到目标目录。
     *
     * @param uid   用户ID（0 为公共网盘）
     * @param param 移动参数，包括源目录、文件名列表和目标目录
     * @return 空成功响应
     * @throws IOException 文件系统访问异常
     */
    @ApiOperation("移动文件或目录")
    @PostMapping("/move/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_write')")
    public JsonResult<Object> move(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID(true) long uid,
            @RequestBody @Validated OpenApiMoveParam param) throws IOException {
        DiskFileSystem fs = diskFileSystemManager.getMainFileSystem();
        boolean overwrite = param.getIsOverwrite() != null && param.getIsOverwrite();
        for (String name : param.getFiles()) {
            fs.move(uid, param.getSourcePath(), param.getTargetPath(), name, overwrite);
        }
        return JsonResult.emptySuccess();
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
    @ApiOperation("重命名文件或目录")
    @PostMapping("/rename/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_write')")
    public JsonResult<Object> rename(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID(true) long uid,
            @ApiParam(value = "文件所在目录路径", required = true)
            @RequestParam("path") @ValidPath String path,
            @ApiParam(value = "原文件名", required = true)
            @RequestParam("oldName") String oldName,
            @ApiParam(value = "新文件名", required = true)
            @RequestParam("newName") String newName) throws IOException {
        if (newName == null || newName.trim().isEmpty()) {
            throw new JsonException(400, "文件名不能为空");
        }
        diskFileSystemManager.getMainFileSystem().rename(uid, path, oldName, newName);
        return JsonResult.emptySuccess();
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
    @ApiOperation("删除文件或目录")
    @DeleteMapping("/delete/v1")
    @PreAuthorize("hasAuthority('SCOPE_storage_write')")
    public JsonResult<Object> delete(
            @ApiParam(value = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID(true) long uid,
            @ApiParam(value = "文件所在目录路径", required = true)
            @RequestParam("path") @ValidPath String path,
            @RequestBody @Validated FileNameList param) throws IOException {
        diskFileSystemManager.getMainFileSystem().deleteFile(uid, path, param.getFileName());
        return JsonResult.emptySuccess();
    }

    // ========================= Inner DTO =========================

    /**
     * 开放 API 文件复制参数。
     */
    @lombok.Data
    public static class OpenApiCopyParam {

        /**
         * 源文件所在目录路径
         */
        private String sourcePath;

        /**
         * 待复制的文件名列表，为 null 则复制 sourcePath 下的所有文件
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
     * 开放 API 文件移动参数。
     */
    @lombok.Data
    public static class OpenApiMoveParam {

        /**
         * 文件当前所在目录路径
         */
        private String sourcePath;

        /**
         * 待移动的文件名列表
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
}
