package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    @Resource
    FileService fileService;
    @PostMapping("private/**")
    public JsonResult uploadPrivate(HttpServletRequest request, @RequestParam("file") MultipartFile file) throws HasResultException, IOException {
        String path = URLUtils.getRequestFilePath("/api/upload/private", request);
        String userBasePath = DiskConfig.getUserPrivatePath();
        String targetFilePath = userBasePath + path + "/" + file.getOriginalFilename();
        int i = fileService.saveUploadFile(targetFilePath, file);
        return JsonResult.getInstance(i == 0 ? "add" : "cover");
    }
}
