package com.xiaotao.saltedfishcloud.service.breakpoint.manager;

import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskStatMetadata;

import java.io.InputStream;

public interface TaskManager {

    /**
     * 创建断点续传任务
     * @param info  任务元数据
     * @return      创建成功后的任务ID
     */
    String createTask(TaskMetadata info) throws Exception;

    /**
     * 查询任务信息
     * @param id 任务ID
     * @return 任务信息，若任务不存在则返回Null
     */
    TaskStatMetadata queryTask(String id) throws Exception;

    /**
     * 清理指定的任务数据
     * @param id    任务ID
     */
    void clear(String id) throws Exception;

    /**
     * 保存部分的断点续传任务文件片段
     * @param id        任务ID
     * @param part      文件块编号（从1开始）
     * @param stream    文件流
     */
    void save(String id, String part, InputStream stream) throws Exception;
}
