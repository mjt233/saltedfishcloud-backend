package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 移动/重命名资源控制器
 */
@RestController
@RequestMapping("/api")
public class MoveController {
    @Resource
    private FileService fileService;

    @PostMapping("/rename/{uid}/**")
    @Transactional(rollbackFor = Exception.class)
    public JsonResult move(HttpServletRequest request,
                           @PathVariable int uid,
                           @RequestParam("oldName") String oldName,
                           @RequestParam("newName") String newName) throws HasResultException {
        UIDValidator.validate(uid, true);
        String from = URLUtils.getRequestFilePath("/api/rename/" + uid, request);
        if (newName.length() < 1) {
            throw new HasResultException(400, "文件名不能为空");
        }
        fileService.rename(uid, from, oldName, newName);
        return JsonResult.getInstance();
    }
}
