package com.xiaotao.saltedfishcloud.controller.task;

import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.service.download.DownloadService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.nio.file.NoSuchFileException;

@RestController
@RequestMapping("/api/task/download")
public class DownloadController {
    @Resource
    private DownloadService downloadService;

    @PostMapping
    public JsonResult createTask(@RequestBody @Validated DownloadTaskParams info) throws NoSuchFileException {
        return JsonResult.getInstance(downloadService.createTask(info, SecureUtils.getSpringSecurityUser().getId()));
    }
}
