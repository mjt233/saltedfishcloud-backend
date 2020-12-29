package com.xiaotao.saltedfishcloud.controller.file;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.po.FileCacheInfo;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;

@RestController
public class ResourcesController {
    @Resource
    FileService fileService;
    @RequestMapping("/api/publicSearch/**")
    @ResponseBody
    public JsonResult search(HttpServletRequest request, String key,@RequestParam(value = "page", defaultValue = "1") Integer page) {
        PageHelper.startPage(page, 10);
        String filePath = URLUtils.getRequestFilePath("/api/publicSearch", request);
        key = "%" + key + "%";
        PageInfo<FileCacheInfo> pageInfo = new PageInfo<>(fileService.search(key));
        return JsonResult.getInstance(pageInfo);
    }

    @RequestMapping("/api/Resources/updateCache")
    @RolesAllowed({"ADMIN"})
    public JsonResult updateCache() {
        fileService.updateCache();
        return JsonResult.getInstance();
    }
}
