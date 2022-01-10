package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.entity.JsonResult;
import com.xiaotao.saltedfishcloud.entity.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;
import lombok.var;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.NoSuchFileException;

/**
 * 断点续传管理控制器的实现类
 */
public class BreakPointControllerImpl implements BreakPointController {
    private final TaskManager manager;
    public BreakPointControllerImpl(TaskManager manager) {
        this.manager = manager;
    }

    @Override
    public Object uploadPart(MultipartFile file, String id, String part) throws Exception {
        manager.save(id, part, file.getInputStream());
        return JsonResult.emptySuccess();
    }

    @Override
    public TaskMetadata createTask(@Validated TaskMetadata data) throws Exception {
        String taskId = manager.createTask(data);
        data.setTaskId(taskId);
        return data;
    }

    @Override
    public TaskMetadata queryTask(String id) throws Exception {
        var res = manager.queryTask(id);
        if (res == null) {
            throw new TaskNotFoundException(id);
        }
        return res;
    }

    @Override
    public Object clearTask(String id) throws Exception {
        try {
            manager.clear(id);
        } catch (NoSuchFileException e) {
            throw new TaskNotFoundException(id);
        }
        return JsonResult.emptySuccess();
    }
}
