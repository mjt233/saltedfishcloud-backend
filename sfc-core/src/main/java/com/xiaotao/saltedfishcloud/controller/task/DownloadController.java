package com.xiaotao.saltedfishcloud.controller.task;

import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.entity.json.JsonResult;
import com.xiaotao.saltedfishcloud.entity.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.entity.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.entity.po.param.TaskType;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.download.DownloadService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.nio.file.NoSuchFileException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/task/download")
@Validated
public class DownloadController {
    private final DownloadService downloadService;
    private final ProxyDao proxyDao;

    @GetMapping("proxy")
    public JsonResult getProxy() {
        var res = proxyDao.getAllProxy();
        res.forEach(e -> {
            e.setAddress(null);
            e.setPort(null);
            e.setType(null);
        });
        return JsonResultImpl.getInstance(res);
    }

    @DeleteMapping
    public JsonResult interrupt(@RequestParam String taskId) {
        downloadService.interrupt(taskId);
        return JsonResult.emptySuccess();
    }

    @PostMapping
    public JsonResult createTask(@RequestBody @Validated DownloadTaskParams info) throws NoSuchFileException {
        return JsonResultImpl.getInstance(downloadService.createTask(info, SecureUtils.getSpringSecurityUser().getId()));
    }

    @GetMapping
    public JsonResult getAllTask(
            @UID @RequestParam @Validated int uid,
            @RequestParam(defaultValue = "1") @Validated @Min(1) int page,
            @RequestParam(defaultValue = "10") @Validated @Min(5) @Max(400) int size,
            @RequestParam(defaultValue = "ALL") TaskType type
    ) {
        Page<DownloadTaskInfo> res = downloadService.getTaskList(uid, page - 1, size, type);
        return JsonResultImpl
                .getInstance(res.getContent())
                .put("totalItem", res.getTotalElements())
                .put("totalPage", res.getTotalPages());
    }
}
