package com.xiaotao.saltedfishcloud.controller.file;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;

import com.xiaotao.saltedfishcloud.validator.custom.FileName;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.NoSuchFileException;

/**
 * 创建目录控制器
 */
@RestController
@RequestMapping("/api")
@Validated
public class MkdirController {
    @Resource
    FileService fileService;

    @PostMapping("mkdir/{uid}/**")
    @Transactional(rollbackFor = Exception.class)
    public JsonResult mkdir(@PathVariable int uid,
                               HttpServletRequest request,
                               @RequestParam("name") @Valid @FileName String name) throws HasResultException, NoSuchFileException {
        UIDValidator.validate(uid, true);
        String prefix = "/api/mkdir/" + uid;
        String requestPath = URLUtils.getRequestFilePath(prefix, request);
        fileService.mkdir(uid, requestPath, name);
        return JsonResult.getInstance();

    }
}
