package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

public interface BreakPointController {

    @ResponseBody
    @PostMapping
    TaskMetadata createTask(TaskMetadata data) throws Exception;

    @ResponseBody
    @GetMapping
    TaskMetadata queryTask(String id) throws Exception;

    @ResponseBody
    @DeleteMapping
    Object clearTask(String id) throws Exception;
}
