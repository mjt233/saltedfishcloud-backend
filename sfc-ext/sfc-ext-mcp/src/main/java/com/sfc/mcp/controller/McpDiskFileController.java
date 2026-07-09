package com.sfc.mcp.controller;

import com.sfc.mcp.model.McpOperationResult;
import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * MCP 模块网盘文件传输 Controller。
 * <p>
 * 提供文件上传和下载的 HTTP 接口，供 MCP Agent 通过 HTTP 请求直接调用。
 * 鉴权通过 {@link com.sfc.mcp.security.McpApiKeyFilter} 完成，不依赖 OAuth 开放平台。
 * </p>
 */
@RestController
@RolesAllowed(SysRole.OAUTH_USER)
@RequestMapping("/api/mcp/diskFile")
@RequiredArgsConstructor
@Validated
public class McpDiskFileController {

    private final DiskFileSystemManager diskFileSystemManager;

    private final FileLinkService fileLinkService;

    /**
     * 下载指定用户网盘中指定路径的文件。
     *
     * @param uid  用户 ID（0 为公共网盘）
     * @param path 文件完整路径（包含文件名），以 "/" 开头
     * @return HTTP 响应，包含文件内容和相应响应头
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "MCP 下载网盘文件")
    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID long uid,
            @Parameter(description = "文件完整路径（包含文件名）", required = true)
            @RequestParam("path") @ValidPath String path) throws IOException {
        String parentPath = PathUtils.getParentPath(path);
        String fileName = PathUtils.getLastNode(path);

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
     * @param uid     用户 ID（0 为公共网盘）
     * @param path    文件完整路径（包含文件名）
     * @param request HTTP 请求对象
     * @return 临时下载链接
     */
    @Operation(summary = "MCP 创建网盘文件下载链接")
    @GetMapping("/downloadLink")
    public JsonResult<String> createDownloadLink(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID long uid,
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
        return JsonResultImpl.getInstance(fileLinkService.createLink(baseUrl, resourceRequest));
    }

    /**
     * 上传文件到指定用户网盘的指定目录。
     *
     * @param uid  用户 ID（0 为公共网盘）
     * @param path 文件保存的目标目录路径，以 "/" 开头
     * @param file 上传的文件内容
     * @return 操作结果码（0-覆盖，1-新文件，2-无变化）
     * @throws IOException 文件系统访问异常
     */
    @Operation(summary = "MCP 上传文件到网盘")
    @PostMapping("/upload")
    public JsonResult<Long> upload(
            @Parameter(description = "用户ID，0 表示公共网盘", required = true)
            @RequestParam("uid") @UID(true) long uid,
            @Parameter(description = "目标目录路径", required = true)
            @RequestParam("path") String path,
            @Parameter(description = "上传的文件", required = true)
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
}
