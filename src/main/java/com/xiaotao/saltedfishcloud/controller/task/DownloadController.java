package com.xiaotao.saltedfishcloud.controller.task;

import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.service.download.DownloadService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.UID;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.nio.file.NoSuchFileException;

@RestController
@RequestMapping("/api/task/download")
@Validated
public class DownloadController {
    @Resource
    private DownloadService downloadService;

    @PostMapping
    public JsonResult createTask(@RequestBody @Validated DownloadTaskParams info) throws NoSuchFileException {
        return JsonResult.getInstance(downloadService.createTask(info, SecureUtils.getSpringSecurityUser().getId()));
    }

    @GetMapping
    public JsonResult getAllTask(
            @UID @RequestParam @Validated int uid,
            @RequestParam(defaultValue = "1") @Validated @Min(1) int page,
            @RequestParam(defaultValue = "10") @Validated @Min(5) @Max(400) int size
    ) {
        Page<DownloadTaskInfo> res = downloadService.getTaskList(uid, page - 1, size);
        return JsonResult
                .getInstance(res.getContent())
                .put("total_item", res.getTotalElements())
                .put("total_page", res.getTotalPages());
    }
}
