package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
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
    public JsonResult uploadPrivate(HttpServletRequest request,
                                    @RequestAttribute User user,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "md5", required = false) String md5) throws HasResultException, IOException {
        String path = URLUtils.getRequestFilePath("/api/private", request);
        String userBasePath = DiskConfig.getUserPrivatePath();
        String targetFilePath = userBasePath + path + "/" + file.getOriginalFilename();
        //  存储文件
        int i = fileService.storeUploadFile(targetFilePath, file);

        //  若前端有传入md5则直接使用前端传入的值
        FileInfo fileInfo = new FileInfo(new File(targetFilePath));
        if (md5 != null) {
            fileInfo.setMd5(md5);
        }
        if (i == 0) {
            fileService.addPrivateFileCacheInfo(user.getId(), fileInfo);
        } else {
            fileService.updatePrivateFileCacheInfo(user.getId(), fileInfo);
        }
        return JsonResult.getInstance(i == 0 ? "add" : "cover");
    }
}
