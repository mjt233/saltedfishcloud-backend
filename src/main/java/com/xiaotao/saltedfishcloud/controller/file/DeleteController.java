package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.po.FileNameList;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 删除资源相关控制器
 */
@RestController
@RequestMapping("/api")
public class DeleteController {
    @Resource
    FileService fileService;
    @DeleteMapping("private/**")
    public JsonResult deletePrivate(HttpServletRequest request, @RequestBody FileNameList fileName, @RequestAttribute User user) {
        String path = URLUtils.getRequestFilePath("/api/private", request);
        fileService.deleteFile(user.getId(), path, fileName.getFileName());
        return JsonResult.getInstance();
    }
}
