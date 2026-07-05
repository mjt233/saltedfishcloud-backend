package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.resource.FileLinkService;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * 文件临时链接控制器。
 */
@RestController
@RequestMapping("/api/fileLink")
@Validated
@RequiredArgsConstructor
public class FileLinkController {
    /**
     * 文件临时链接服务。
     */
    private final FileLinkService fileLinkService;

    /**
     * 系统资源服务。
     */
    private final ResourceService resourceService;

    /**
     * 创建临时下载链接。
     *
     * @param baseUrl         下载接口地址
     * @param resourceRequest 资源请求参数
     * @return 完整临时下载链接
     * @throws IOException 读取资源异常
     */
    @ApiOperation("创建临时下载链接")
    @PostMapping("/create")
    public JsonResult<String> createLink(@RequestParam("baseUrl") String baseUrl,@RequestBody @Valid ResourceRequest resourceRequest) throws IOException {
        return JsonResultImpl.getInstance(fileLinkService.createLink(baseUrl, resourceRequest));
    }

    /**
     * 通过临时链接下载文件。
     *
     * @param request HTTP 请求对象
     * @return 文件响应
     * @throws IOException 读取资源异常
     */
    @ApiOperation("通过临时链接下载文件")
    @GetMapping("/download")
    @AllowAnonymous
    public ResponseEntity<Resource> download(HttpServletRequest request) throws IOException {
        String query = request.getQueryString();
        String fullUrl = request.getRequestURL() + (query == null ? "" : "?" + query);
        Resource resource = fileLinkService.parseLink(fullUrl);
        String filename = resource.getFilename() == null ? "file" : resource.getFilename();
        return ResourceUtils.wrapResource(resource, filename);
    }
}



