package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;

@RestController
@RequestMapping("/api")
public class MkdirController {
    @Resource
    FileService fileService;
    @PostMapping("private/**")
    public JsonResult privateMkdir(HttpServletRequest request, @RequestParam("name") String name) {
        String requestPath = URLUtils.getRequestFilePath("/api/private", request);
        String basePath = DiskConfig.getUserPrivatePath();
        String path = basePath + "/" + requestPath + "/" + name;
        File targetFile = new File(path);
        boolean res = targetFile.mkdir();
        if (res) {
            fileService.addPrivateFileCacheInfo(SecureUtils.getSpringSecurityUser().getId(), new FileInfo(targetFile));
            return JsonResult.getInstance();
        } else {
            return JsonResult.getInstance(500, null, "创建失败");
        }

    }
}
