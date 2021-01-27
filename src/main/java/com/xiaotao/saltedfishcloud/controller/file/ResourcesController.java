package com.xiaotao.saltedfishcloud.controller.file;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.po.FileCacheInfo;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ResourcesController {
    @Resource
    FileService fileService;
    @GetMapping("/search/public/**")
    public JsonResult search(HttpServletRequest request, String key,@RequestParam(value = "page", defaultValue = "1") Integer page) {
        PageHelper.startPage(page, 10);
        PageInfo<FileCacheInfo> pageInfo = new PageInfo<>(fileService.search(key));
        return JsonResult.getInstance(pageInfo);
    }

    @PostMapping("updateCache")
    @RolesAllowed({"ADMIN"})
    public JsonResult updateCache() {
        fileService.updateCache();
        return JsonResult.getInstance();
    }
}
