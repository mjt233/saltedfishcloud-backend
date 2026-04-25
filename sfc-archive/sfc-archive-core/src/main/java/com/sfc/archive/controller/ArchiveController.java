package com.sfc.archive.controller;

import com.sfc.archive.model.AsyncArchiveExtractParam;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.validator.ValidPathValidator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Optional;

/**
 * 压缩包操作控制器，提供文件在线解压等异步操作接口。
 */
@RestController
@RequestMapping("/api/archive")
@Validated
@RequiredArgsConstructor
@Api(tags = "压缩包操作")
public class ArchiveController {

    private final AsyncTaskManager asyncTaskManager;

    /**
     * 创建文件在线解压异步任务。
     * <p>
     * 通过 {@link AsyncArchiveExtractParam} 指定待解压文件来源（支持任意协议的资源请求）、
     * 解压参数（格式、编码、密码等）以及解压目标用户和目录。
     * 任务提交后立即返回任务 ID，客户端可通过任务 ID 查询执行进度与日志。
     * </p>
     *
     * @param param 解压任务参数，包含源文件资源请求、解压参数、目标 uid 与路径
     * @return 提交成功的异步任务 ID
     * @throws IOException 构建任务参数时发生 IO 异常
     */
    @PostMapping("asyncExtract")
    @ApiOperation("创建文件在线解压异步任务")
    public JsonResult<Long> asyncExtract(@RequestBody AsyncArchiveExtractParam param) throws IOException {
        // UID 安全校验：公共资源（uid=0）的写入仅允许管理员
        UIDValidator.validateWithException(param.getUid(), true);
        ValidPathValidator.valid(param.getPath());

        // 构建异步任务记录
        AsyncTaskRecord record = new AsyncTaskRecord();
        record.setName("解压文件: " + Optional.ofNullable(param.getSource())
                .map(ResourceRequest::getName)
                .orElse("未知文件")
                + " -> " + param.getPath());
        record.setTaskType(AsyncTaskType.ARCHIVE_EXTRACTOR);
        record.setCpuOverhead(20);
        record.setParams(MapperHolder.toJson(param));

        // 关联当前登录用户
        User curUser = SecureUtils.getSpringSecurityUser();
        record.setUid(Optional.ofNullable(curUser).map(User::getId).orElse(param.getUid()));

        // 提交任务
        asyncTaskManager.submitAsyncTask(record);
        return JsonResultImpl.getInstance(record.getId());
    }
}

