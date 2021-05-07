package com.xiaotao.saltedfishcloud.controller.file;

import java.io.IOException;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传资源相关控制器
 */
@RestController
@RequestMapping(value = "/api", method = RequestMethod.PUT)
@Validated
public class UploadController {
    @Resource
    FileService fileService;

    /**
     * 上传文件到网盘系统中
     * @param uid   目标用户的ID
     * @param file  接收到的文件
     * @param md5   文件MD5
     */
    @RequestMapping("fileList/{uid}/**")
    @Transactional(rollbackFor = Exception.class)
    public JsonResult upload(HttpServletRequest request,
                                    @PathVariable int uid,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "md5", required = false) String md5) throws HasResultException, IOException {
        UIDValidator.validate(uid, true);
        String prefix = "/api/fileList/" + uid;
        String requestPath = URLUtils.getRequestFilePath(prefix, request);
        int i = fileService.saveFile(uid, file, requestPath, md5);
        return JsonResult.getInstance(i);
    }
}
