package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping(value = "/api", method = RequestMethod.PUT)
public class UploadController {
    @Resource
    FileService fileService;
    @RequestMapping("private/**")
    public JsonResult uploadPrivate(HttpServletRequest request, @RequestParam("file") MultipartFile file) throws HasResultException, IOException {
        User user = SecureUtils.getSpringSecurityUser();
        String path = URLUtils.getRequestFilePath("/api/private", request);
        String userBasePath = DiskConfig.getUserPrivatePath();
        String targetFilePath = userBasePath + path + "/" + file.getOriginalFilename();
        int i = fileService.saveUploadFile(targetFilePath, file);
        FileInfo fileInfo = new FileInfo(new File(targetFilePath));
        if (i == 0) {
            fileService.addPrivateFileCacheInfo(user.getId(), fileInfo);
        } else {
            fileService.updatePrivateFileCacheInfo(user.getId(), fileInfo);
        }
        return JsonResult.getInstance(i == 0 ? "add" : "cover");
    }
}
