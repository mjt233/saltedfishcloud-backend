package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping(value = "/api", method = RequestMethod.PUT)
public class UploadController {
    @Resource
    FileService fileService;

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
