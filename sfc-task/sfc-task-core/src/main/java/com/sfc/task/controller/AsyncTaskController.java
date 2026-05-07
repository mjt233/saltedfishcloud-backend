package com.sfc.task.controller;

import com.sfc.task.AsyncTaskManager;
import com.sfc.task.AsyncTaskRecordService;
import com.sfc.task.model.AsyncTaskQueryParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressDetector;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.prog.ProgressRecordVO;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/asyncTask")
public class AsyncTaskController {
    @Autowired
    private AsyncTaskManager asyncTaskManager;

    @Autowired
    private AsyncTaskRecordRepo asyncTaskRecordRepo;

    @Autowired
    private AsyncTaskRecordService asyncTaskRecordService;

    @Autowired
    private ProgressDetector progressDetector;

    /**
     * 获取任务执行进度信息
     */
    @PostMapping("getProg")
    public JsonResult<List<ProgressRecordVO>> getProg(@RequestBody List<Long> taskId) {
        return JsonResultImpl.getInstance(taskId.stream().map(id -> {
            ProgressRecord record = progressDetector.getRecord(id.toString());
            if (record == null) {
                return null;
            }
            return ProgressRecordVO.builder()
                    .taskId(id.toString())
                    .record(record)
                    .build();
        }).filter(Objects::nonNull).toList());
    }

    @GetMapping("listRecord")
    public JsonResult<CommonPageInfo<AsyncTaskRecord>> listRecord(AsyncTaskQueryParam param) {
        return JsonResultImpl.getInstance(asyncTaskRecordService.listRecord(param));
    }

    @GetMapping("getById")
    public JsonResult<AsyncTaskRecord> getById(@RequestParam("taskId") Long taskId) {
        return JsonResultImpl.getInstance(asyncTaskRecordRepo.getReferenceById(taskId));
    }

    /**
     * 阻塞等待执行完成。
     * @param taskId        等待的任务id
     * @param timeout       最长等待时长
     * @return  是否执行完成
     */
    @GetMapping("waitTaskExit")
    public JsonResult<Boolean> waitTaskExit(@RequestParam("taskId") Long taskId,
                                        @RequestParam("timeout") Long timeout) throws IOException {
        try {
            asyncTaskManager.waitTaskExit(taskId, timeout, TimeUnit.SECONDS);
            return JsonResultImpl.getInstance(true);
        } catch (InterruptedException e) {
            return JsonResultImpl.getInstance(false);
        }
    }

    /**
     * 重新运行任务
     * @param taskId    任务id
     * @return          新任务对象
     */
    @PostMapping("rerun")
    public JsonResult<AsyncTaskRecord> rerun(@RequestParam("taskId") Long taskId) throws IOException {
        return JsonResultImpl.getInstance(asyncTaskManager.rerun(taskId));
    }

    @RequestMapping("/interrupt")
    public JsonResult<Object> interrupt(@RequestParam("taskId") Long taskId) throws IOException {
        AsyncTaskRecord record = asyncTaskRecordRepo.getReferenceById(taskId);
        UIDValidator.validate(record.getUid(), true);
        asyncTaskManager.interrupt(taskId);
        return JsonResult.emptySuccess();
    }

    /**
     * 获取任务日志
     * @param taskId  任务id
     * @return         日志正文
     */
    @GetMapping("getLog")
    public JsonResult<String> getLog(@RequestParam("taskId") Long taskId) throws IOException {
        return JsonResultImpl.getInstance(ResourceUtils.resourceToString(asyncTaskManager.getTaskLog(taskId, true)));
    }
}
