package com.sfc.task.controller;

import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/asyncTask")
public class AsyncTaskController {
    @Autowired
    private AsyncTaskManager asyncTaskManager;

    @Autowired
    private AsyncTaskRecordRepo asyncTaskRecordRepo;

    @RequestMapping("/interrupt")
    public JsonResult interrupt(@RequestParam("taskId") Long taskId) throws IOException {
        AsyncTaskRecord record = asyncTaskRecordRepo.getOne(taskId);
        UIDValidator.validate(record.getUid(), true);
        asyncTaskManager.interrupt(taskId);
        return JsonResult.emptySuccess();
    }
}
