package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.po.FileNameList;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 删除资源相关控制器
 * @// TODO: 2020/12/30   删除时连同数据库文件信息缓存
 */
@RestController
@RequestMapping("/api")
public class DeleteController {
    @Resource
    FileService fileService;
    @DeleteMapping("private/**")
    public JsonResult deletePrivate(HttpServletRequest request, @RequestBody FileNameList fileName) {
        String target = DiskConfig.getUserPrivatePath() + "/" + URLUtils.getRequestFilePath("/api/private", request);
        fileName.getFileName().forEach(name -> {
            String path = target + "/" + name;
            fileService.deletePrivateFileCache(SecureUtils.getSpringSecurityUser().getId(), new FileInfo(new File(path)));
            fileService.deleteFile(path);
        });
        return JsonResult.getInstance();
    }
}
