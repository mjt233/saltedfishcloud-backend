package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.po.FileNameList;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.transaction.annotation.Transactional;
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

    @DeleteMapping("resource/{uid}/**")
    @Transactional(rollbackFor = Exception.class)
    public JsonResult delete(HttpServletRequest request,
                             @PathVariable int uid,
                             @RequestBody FileNameList fileName,
                             @RequestAttribute User user) {
        UIDValidator.validate(uid, true);
        String path = URLUtils.getRequestFilePath("/api/resource/" + uid, request);
        fileService.deleteFile(uid, path, fileName.getFileName());
        return JsonResult.getInstance();
    }
}
