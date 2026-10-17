package com.sfc.ext.download.controller;

import com.sfc.ext.download.DownloadService;
import com.sfc.ext.download.model.po.DownloadTaskInfo;
import com.sfc.ext.download.model.DownloadTaskParams;
import com.sfc.common.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.model.param.TaskType;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.sfc.cloud.data.validation.SfcUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/task/download")
@Validated
public class DownloadController {
    private final DownloadService downloadService;

    @DeleteMapping
    public SfcJsonResult<Object> interrupt(@RequestParam String taskId) throws IOException {
        downloadService.interrupt(taskId);
        return SfcJsonResult.emptySuccess();
    }

    @PostMapping
    public SfcJsonResult<String> createTask(@RequestBody @Validated DownloadTaskParams info) throws IOException {
        return SfcJsonResult.getInstance(downloadService.createTask(info, SecureUtils.getSpringSecurityUser().getId()));
    }

    @GetMapping
    public SfcJsonResult<Page<DownloadTaskInfo>> getAllTask(
            @SfcUID @RequestParam @Validated long uid,
            @RequestParam(defaultValue = "1") @Validated @Min(1) int page,
            @RequestParam(defaultValue = "10") @Validated @Min(5) @Max(400) int size,
            @RequestParam(defaultValue = "ALL") TaskType type
    ) {
        Page<DownloadTaskInfo> res = downloadService.getTaskList(uid, page - 1, size, type);
        return SfcJsonResult.getInstance(res);
    }
}
