package com.xiaotao.saltedfishcloud.controller.file;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.po.FileCacheInfo;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ResourcesController {
    @Resource
    FileService fileService;
    @GetMapping("/publicSearch/**")
    public JsonResult search(HttpServletRequest request, String key,@RequestParam(value = "page", defaultValue = "1") Integer page) {
        PageHelper.startPage(page, 10);
        String filePath = URLUtils.getRequestFilePath("/api/publicSearch", request);
        PageInfo<FileCacheInfo> pageInfo = new PageInfo<>(fileService.search(key));
        return JsonResult.getInstance(pageInfo);
    }

    @GetMapping("searchWithSqlInject")
    public JsonResult searchWithSqlInject(String key,@RequestParam(value = "page", defaultValue = "1") Integer page) {
//        PageHelper.startPage(page, 10);
//        PageInfo<FileCacheInfo> data = new PageInfo<>(fileService.searchWithSqlInject(key));
//        return JsonResult.getInstance(data);
        return JsonResult.getInstance(fileService.searchWithSqlInject(key));
    }

    @RequestMapping("updateCache")
    @RolesAllowed({"ADMIN"})
    public JsonResult updateCache() {
        fileService.updateCache();
        return JsonResult.getInstance();
    }
}
