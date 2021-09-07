package com.xiaotao.saltedfishcloud.controller.task;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.service.download.DownloadService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.UID;
import lombok.var;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.nio.file.NoSuchFileException;

@RestController
@RequestMapping("/api/task/download")
@Validated
public class DownloadController {
    @Resource
    private DownloadService downloadService;

    @DeleteMapping
    public JsonResult interrupt(@RequestParam String taskId) {
        var context = downloadService.getTaskContext(taskId);
        if (context == null) throw new JsonException(404, taskId + "不存在");
        var loginUser = SecureUtils.getSpringSecurityUser();
        if (context.getTask().bindingInfo.uid != loginUser.getId() && loginUser.getType() != User.TYPE_ADMIN) {
            throw new JsonException(403, "无权操作");
        } else {
            context.interrupt();
        }
        return JsonResult.getInstance();
    }

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
                .put("totalItem", res.getTotalElements())
                .put("totalPage", res.getTotalPages());
    }
}
