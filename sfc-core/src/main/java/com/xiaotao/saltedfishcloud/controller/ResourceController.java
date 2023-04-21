package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.sfc.constant.error.FileSystemError;
import com.sfc.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.resource.ResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统资源管理控制器
 */
@RestController
@RequestMapping(ResourceController.PREFIX + "{uid}")
@Validated
@ProtectBlock
@RequiredArgsConstructor
public class ResourceController {
    public static final String PREFIX = "/api/resource/";
    private final DiskFileSystemManager fileService;
    private final NodeService nodeService;
    private final ResourceService resourceService;
    private final ThumbnailService thumbnailService;

    @ApiOperation("上传文件到目标资源")
    @PutMapping("upload")
    @AllowAnonymous
    public JsonResult<Object> uploadResource(@Validated ResourceRequest resourceRequest,
                                             HttpServletRequest request,
                                             @MergeFile MultipartFile file
                                             ) throws UnsupportedProtocolException, IOException {
        this.mergeParams(resourceRequest, request);
        ResourceProtocolHandler handler = resourceService.getResourceHandler(resourceRequest.getProtocol());
        if (handler == null) {
            throw new UnsupportedProtocolException(resourceRequest.getProtocol());
        }
        if (!handler.isWriteable()) {
            throw new IllegalArgumentException("目标资源不支持数据写入");
        }
        if (file == null) {
            throw new IllegalArgumentException("缺少文件");
        }
        if (resourceRequest.getParams().containsKey(ResourceRequest.CREATE_UID)) {
            throw new IllegalArgumentException("不允许的特殊参数：createUId");
        }
        handler.writeResource(resourceRequest, file.getResource());
        return JsonResult.emptySuccess();
    }

    /**
     * 读取所有请求参数，设置到params中
     * @param resourceRequest   通用资源请求参数
     * @param request           http请求对象
     */
    private void mergeParams(ResourceRequest resourceRequest, HttpServletRequest request) {
        Map<String, String> paramsMap = new HashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            paramsMap.put(entry.getKey(), entry.getValue()[0]);
        }
        resourceRequest.setParams(paramsMap);
    }

    @GetMapping("get")
    @AllowAnonymous
    public HttpEntity<Resource> getResource(@Validated ResourceRequest resourceRequest, HttpServletRequest request) throws UnsupportedProtocolException, IOException {
        this.mergeParams(resourceRequest, request);

        // 根据请求对象获取资源
        Resource resource = resourceService.getResource(resourceRequest);
        if (resource == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        return ResourceUtils.wrapResource(resource, resourceRequest.getName());
    }

    @GetMapping("thumbnail/{md5}")
    @AllowAnonymous
    public HttpEntity<Resource> getThumbnail(@PathVariable("md5") String md5, @RequestParam("type") String type, HttpServletResponse response) throws IOException {
        Resource img = thumbnailService.getThumbnail(md5, type);
        if (img == null) {
            response.setStatus(404);
            return null;
        }
        return ResourceUtils.wrapResourceWithCache(img, md5 + ".jpg");
    }

    @GetMapping({"node/**", "node"})
    @AllowAnonymous
    @NotBlock
    public JsonResult<Object> pathToNodeList(@PathVariable("uid") @UID int uid, HttpServletRequest request) throws NoSuchFileException {
        String path = URLUtils.getRequestFilePath(PREFIX + "/" + uid + "/node", request);
        return JsonResultImpl.getInstance(nodeService.getPathNodeByPath(uid, "/" + path));
    }

    /**
     * 解析节点ID，获取节点ID对应的文件夹路径
     * @param uid   用户ID
     * @param node  节点ID
     */
    @GetMapping("path/{node}")
    @AllowAnonymous
    @NotBlock
    public JsonResult<Object> getPath(@PathVariable("uid") @UID int uid,
                              @PathVariable("node") String node) {
        return JsonResultImpl.getInstance(nodeService.getPathByNode(uid, node));
    }

    /**
     * 获取文件下载码 FDC - File Download Code
     */
    @GetMapping("FDC/**")
    @AllowAnonymous
    @NotBlock
    public JsonResult<Object> getFDC(@PathVariable @UID int uid,
                             HttpServletRequest request,
                             @RequestParam(value = "md5", required = false) String md5,
                             @RequestParam("name") @Valid @FileName String name,
                             @RequestParam(value = "expr", defaultValue = "1") int expr) throws IOException {
        String filePath = URLUtils.getRequestFilePath(PREFIX + uid + "/FDC", request);
        BasicFileInfo fileInfo = new BasicFileInfo(name, md5);
        String dc = resourceService.getFileDownloadCode(uid, filePath, fileInfo, expr);
        return JsonResultImpl.getInstance(dc);
    }



    /**
     * @param code 文件下载码
     */
    @GetMapping("fileContentByFDC/{code}/**")
    @AllowAnonymous
    @ResponseBody
    @NotBlock(level = ProtectLevel.DATA_CHECKING)
    public ResponseEntity<org.springframework.core.io.Resource> downloadByFDC(@PathVariable String code,
                                                                              @RequestParam(required = false, defaultValue = "false") boolean download)
            throws IOException {
        return resourceService.getResourceByDownloadCode(code, download);
    }


    /**
     * 通过MD5获取网盘中的文件
     * @param md5 文件MD5
     */
    @RequestMapping(value = "fileContentByMD5/{md5}/**", method = RequestMethod.GET)
    @AllowAnonymous
    @NotBlock(level = ProtectLevel.DATA_CHECKING)
    public ResponseEntity<org.springframework.core.io.Resource> downloadByMD5(
            @PathVariable("md5") String md5,
            @PathVariable("uid") int uid,
            HttpServletRequest request
    )
            throws IOException {
        Resource resource = fileService.getMainFileSystem().getResourceByMd5(md5);
        String path = URLUtils.getRequestFilePath(PREFIX + uid + "/fileContentByMD5/" + md5, request);
        String name;
        if (path.length() > 1) {
            name = path.substring(path.lastIndexOf('/') + 1);
            if (name.length() == 0) name = resource.getFilename();
        } else {
            name = resource.getFilename();
        }
        return ResourceUtils.wrapResourceWithCache(resource, name);
    }

}
