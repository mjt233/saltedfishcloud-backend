package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 断点续传管理控制器接口，用于处理和响应Web请求的断点续传任务管理API
 */
public interface BreakPointController {

    /**
     * 创建断点续传任务
     * @param data 任务元数据，应当包含fileName和length
     * @return 任务被创建完后完整的任务元数据，必须包含任务ID
     */
    @ResponseBody
    @PostMapping
    TaskMetadata createTask(TaskMetadata data) throws Exception;


    /**
     * 查询断点续传任务信息状态
     * @param id    要查询的ID
     * @return      任务数据信息
     */
    @ResponseBody
    @GetMapping
    TaskMetadata queryTask(String id) throws Exception;

    /**
     * 移除断点续传任务并释放资源
     * @param id    要查询的ID
     */
    @ResponseBody
    @DeleteMapping
    Object clearTask(String id) throws Exception;
}
