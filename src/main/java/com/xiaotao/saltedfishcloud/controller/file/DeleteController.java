package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 删除资源相关控制器
 * @TODO 删除时连同数据库文件信息缓存
 */
@RestController
@RequestMapping("/api/delete")
public class DeleteController {
    @Resource
    FileService fileService;
    @RequestMapping("/private/**")
    public JsonResult deletePrivate(HttpServletRequest request) {
        String target = URLUtils.getRequestFilePath("/api/delete/private", request);
        String path = DiskConfig.getUserPrivatePath() + "/" + target;
        if (fileService.deleteFile(path) ) {
            return JsonResult.getInstance();
        } else {
            return JsonResult.getInstance(500, path, "删除失败");
        }
    }
}
