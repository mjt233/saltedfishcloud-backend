package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
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

    @PostMapping("/move/private/**")
    public JsonResult move(HttpServletRequest request,
                           @RequestParam("to") String to,
                           @RequestParam("oldName") String oldName,
                           @RequestParam("newName") String newName) {
        String from = URLUtils.getRequestFilePath("/api/private/move", request);
        fileService.move(SecureUtils.getSpringSecurityUser().getId(), from, oldName, to, newName);
        return JsonResult.getInstance(from);
    }
}
