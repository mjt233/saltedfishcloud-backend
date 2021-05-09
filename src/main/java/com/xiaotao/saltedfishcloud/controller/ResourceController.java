package com.xiaotao.saltedfishcloud.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;

import com.xiaotao.saltedfishcloud.validator.custom.FileName;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 系统资源管理控制器
 */
@RestController
@RequestMapping(ResourceController.PREFIX + "{uid}")
@Validated
public class ResourceController {
    public static final String PREFIX = "/api/resource/";
    @Resource
    FileService fileService;
    @Resource
    FileRecordService fileRecordService;
    @Resource
    NodeService nodeService;


    /**
     * 仅限管理员：生成公共文件信息记录并写入到数据库中
     */
    @PostMapping("makePublicRecord")
    @RolesAllowed({"ADMIN"})
    public JsonResult makePublicRecord() throws IOException {
        fileRecordService.makePublicRecord();
        return JsonResult.getInstance();
    }

    /**
     * 解析节点ID，获取节点ID对应的文件夹路径
     * @param uid   用户ID
     * @param node  节点ID
     */
    @GetMapping("path/{node}")
    @AllowAnonymous
    public JsonResult getPath(@PathVariable("uid") int uid,
                              @PathVariable("node") String node) {
        UIDValidator.validate(uid);
        return JsonResult.getInstance(nodeService.getPathByNode(uid, node));
    }

    /**
     * 获取文件下载码 FDC - File Download Code
     */
    @GetMapping("FDC/**")
    @AllowAnonymous
    public JsonResult getFDC(@PathVariable int uid,
                             HttpServletRequest request,
                             @RequestParam("md5") String md5,
                             @RequestParam("name") @Valid @FileName String name,
                             @RequestParam(value = "expr", defaultValue = "1") int expr) throws JsonProcessingException {
        UIDValidator.validate(uid);
        String filePath = URLUtils.getRequestFilePath(PREFIX + uid + "/FDC", request);
        BasicFileInfo fileInfo = new BasicFileInfo(name, md5);
        String dc = fileService.getFileDC(uid, filePath, fileInfo, expr);
        return JsonResult.getInstance(dc);
    }



    /**
     * @TODO 控制Content-Type以实现浏览器可选预览或下载
     * @param code 文件下载码
     */
    @GetMapping(value = "fileContentByFDC/{code}")
    @AllowAnonymous
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> downloadByFDC(@PathVariable String code) throws UnsupportedEncodingException, MalformedURLException {
        return fileService.getResourceByDC(code);
    }
}
