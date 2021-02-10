package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class MoveController {
    @Resource
    private FileService fileService;

    @PostMapping("/rename/private/**")
    @Transactional(rollbackFor = Exception.class)
    public JsonResult move(HttpServletRequest request,
                           @RequestParam("oldName") String oldName,
                           @RequestParam("newName") String newName) throws HasResultException {
        String from = URLUtils.getRequestFilePath("/api/rename/private", request);
        if (newName.length() < 1) {
            throw new HasResultException(400, "文件名不能为空");
        }
        fileService.rename(SecureUtils.getSpringSecurityUser().getId(), from, oldName, newName);
        return JsonResult.getInstance();
    }
}
