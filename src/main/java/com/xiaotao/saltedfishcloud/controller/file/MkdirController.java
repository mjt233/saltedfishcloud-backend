package com.xiaotao.saltedfishcloud.controller.file;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创建目录控制器
 */
@RestController
@RequestMapping("/api")
public class MkdirController {
    @Resource
    FileService fileService;

    @PostMapping("mkdir/{uid}/**")
    @Transactional(rollbackFor = Exception.class)
    public JsonResult mkdir(@PathVariable int uid,
                               HttpServletRequest request,
                               @RequestParam("name") String name) throws HasResultException {
        UIDValidator.validate(uid, true);
        String prefix = "/api/mkdir/" + uid;
        String requestPath = URLUtils.getRequestFilePath(prefix, request);
        fileService.mkdir(uid, requestPath, name);
        return JsonResult.getInstance();

    }
}
