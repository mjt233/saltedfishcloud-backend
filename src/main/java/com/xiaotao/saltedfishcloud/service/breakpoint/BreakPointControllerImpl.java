package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import lombok.var;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

@Component
public class BreakPointControllerImpl implements BreakPointController {
    private final TaskManager manager;
    public BreakPointControllerImpl(TaskManager manager) {
        this.manager = manager;
    }

    @Override
    public TaskMetadata createTask(@Validated TaskMetadata data) throws IOException {
        String taskId = manager.createTask(data);
        data.setTaskId(taskId);
        return data;
    }

    @Override
    public TaskMetadata queryTask(String id) throws Exception {
        var res = manager.queryTask(id);
        if (res == null) {
            throw new FileNotFoundException("任务不存在或已释放");
        }
        return res;
    }

    @Override
    public Object clearTask(String id) throws Exception {
        try {
            manager.clear(id);
        } catch (NoSuchFileException e) {
            return JsonResult.getInstance(404, null, "任务不存在或已释放");
        }
        return JsonResult.getInstance();
    }
}
