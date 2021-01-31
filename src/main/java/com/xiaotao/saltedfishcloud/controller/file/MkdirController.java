package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class MkdirController {
    @Resource
    FileService fileService;
    @PostMapping("private/**")
    public JsonResult privateMkdir(@RequestAttribute User user,
                                   HttpServletRequest request,
                                   @RequestParam("name") String name) throws HasResultException {
        String requestPath = URLUtils.getRequestFilePath("/api/private", request);
        fileService.mkdir(user.getId(), requestPath, name);
        return JsonResult.getInstance();

    }
}
