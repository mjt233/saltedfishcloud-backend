package com.xiaotao.saltedfishcloud.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ReadOnlyBlock;
import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.http.ResponseService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.FileName;
import com.xiaotao.saltedfishcloud.validator.UID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.nio.file.NoSuchFileException;

/**
 * 系统资源管理控制器
 */
@RestController
@RequestMapping(ResourceController.PREFIX + "{uid}")
@Validated
@ReadOnlyBlock
@RequiredArgsConstructor
public class ResourceController {
    public static final String PREFIX = "/api/resource/";
    private final DiskFileSystemFactory fileService;
    private final NodeService nodeService;
    private final ResponseService responseService;
    /**
     * 解析节点ID，获取节点ID对应的文件夹路径
     * @param uid   用户ID
     * @param node  节点ID
     */
    @GetMapping("path/{node}")
    @AllowAnonymous
    @NotBlock
    public JsonResult getPath(@PathVariable("uid") @UID int uid,
                              @PathVariable("node") String node) {
        return JsonResult.getInstance(nodeService.getPathByNode(uid, node));
    }

    /**
     * 获取文件下载码 FDC - File Download Code
     */
    @GetMapping("FDC/**")
    @AllowAnonymous
    @NotBlock
    public JsonResult getFDC(@PathVariable @UID int uid,
                             HttpServletRequest request,
                             @RequestParam("md5") String md5,
                             @RequestParam("name") @Valid @FileName String name,
                             @RequestParam(value = "expr", defaultValue = "1") int expr) throws IOException {
        String filePath = URLUtils.getRequestFilePath(PREFIX + uid + "/FDC", request);
        BasicFileInfo fileInfo = new BasicFileInfo(name, md5);
        String dc = fileService.getFileSystem().getFileDC(uid, filePath, fileInfo, expr);
        return JsonResult.getInstance(dc);
    }



    /**
     * @param code 文件下载码
     */
    @GetMapping("fileContentByFDC/{code}/**")
    @AllowAnonymous
    @ResponseBody
    @NotBlock(level = ReadOnlyLevel.DATA_CHECKING)
    public ResponseEntity<org.springframework.core.io.Resource> downloadByFDC(@PathVariable String code,
                                                                              @RequestParam(required = false, defaultValue = "false") boolean download)
            throws MalformedURLException, UnsupportedEncodingException {
        return responseService.getResourceByDC(code, download);
    }


    /**
     * 通过MD5获取网盘中的文件
     * @param md5 文件MD5
     */
    @RequestMapping(value = "fileContentByMD5/{md5}/**", method = RequestMethod.GET)
    @AllowAnonymous
    public ResponseEntity<org.springframework.core.io.Resource> downloadByMD5(
            @PathVariable("md5") String md5,
            @PathVariable("uid") int uid,
            HttpServletRequest request
    )
            throws IOException {
        FileInfo file = fileService.getFileSystem().getFileByMD5(md5);
        String path = URLUtils.getRequestFilePath(PREFIX + uid + "/fileContentByMD5/" + md5, request);
        String name;
        if (path.length() > 1) {
            name = path.substring(path.lastIndexOf('/') + 1);
            if (name.length() == 0) name = file.getName();
        } else {
            name = file.getName();
        }
        return responseService.sendFile(file.getPath(), name);
    }

}
