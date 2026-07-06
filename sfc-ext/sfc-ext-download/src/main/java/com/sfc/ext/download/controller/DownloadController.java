package com.sfc.ext.download.controller;

import com.sfc.ext.download.DownloadService;
import com.sfc.ext.download.model.po.DownloadTaskInfo;
import com.sfc.ext.download.model.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.TaskType;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/task/download")
@Validated
public class DownloadController {
    private final DownloadService downloadService;

    @GetMapping("proxy")
    public JsonResult<List<ProxyInfo>> getProxy() {
        return JsonResultImpl.getInstance(downloadService.listAvailableProxy());
    }

    @DeleteMapping
    public JsonResult<Object> interrupt(@RequestParam String taskId) throws IOException {
        downloadService.interrupt(taskId);
        return JsonResult.emptySuccess();
    }

    @PostMapping
    public JsonResult<String> createTask(@RequestBody @Validated DownloadTaskParams info) throws IOException {
        return JsonResultImpl.getInstance(downloadService.createTask(info, SecureUtils.getSpringSecurityUser().getId()));
    }

    @GetMapping
    public JsonResult<Page<DownloadTaskInfo>> getAllTask(
            @UID @RequestParam @Validated long uid,
            @RequestParam(defaultValue = "1") @Validated @Min(1) int page,
            @RequestParam(defaultValue = "10") @Validated @Min(5) @Max(400) int size,
            @RequestParam(defaultValue = "ALL") TaskType type
    ) {
        Page<DownloadTaskInfo> res = downloadService.getTaskList(uid, page - 1, size, type);
        return JsonResultImpl.getInstance(res);
    }
}
