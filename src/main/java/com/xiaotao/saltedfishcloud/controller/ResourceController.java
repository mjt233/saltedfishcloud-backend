package com.xiaotao.saltedfishcloud.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ReadOnlyBlock;
import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.http.ResponseService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.FileName;
import com.xiaotao.saltedfishcloud.validator.UID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
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
public class ResourceController {
    public static final String PREFIX = "/api/resource/";
    @Resource
    FileService fileService;
    @Resource
    FileRecordService fileRecordService;
    @Resource
    NodeService nodeService;
    @Resource
    ResponseService responseService;


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
                             @RequestParam(value = "expr", defaultValue = "1") int expr) throws JsonProcessingException {
        String filePath = URLUtils.getRequestFilePath(PREFIX + uid + "/FDC", request);
        BasicFileInfo fileInfo = new BasicFileInfo(name, md5);
        String dc = fileService.getFileDC(uid, filePath, fileInfo, expr);
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
            throws MalformedURLException {
        return responseService.getResourceByDC(code, download);
    }


    /**
     * 通过MD5获取网盘中的文件
     * @param md5 文件MD5
     */
    @RequestMapping(value = "fileContentByMD5/{md5}/**", method = RequestMethod.GET)
    @AllowAnonymous
    public ResponseEntity<org.springframework.core.io.Resource> downloadByMD5(@PathVariable("md5") String md5)
            throws NoSuchFileException, MalformedURLException, UnsupportedEncodingException {
        FileInfo file = fileService.getFileByMD5(md5);
        return responseService.sendFile(file.getPath(), file.getName());
    }

}
