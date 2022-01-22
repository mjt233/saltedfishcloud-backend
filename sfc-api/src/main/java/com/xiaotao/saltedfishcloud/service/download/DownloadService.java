package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.entity.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.entity.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.entity.po.param.TaskType;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import org.springframework.data.domain.Page;

import java.nio.file.NoSuchFileException;

public interface DownloadService {
    TaskContext<AsyncDownloadTask> getTaskContext(String taskId);

    /**
     * 中断任务下载
     * @param id    要中断的下载任务ID
     */
    void interrupt(String id);

    /**
     * 获取用户的所有下载任务
     * @param uid   要查询的用户ID
     */
    Page<DownloadTaskInfo> getTaskList(int uid, int page, int size, TaskType type);

    /**
     * 创建一个下载任务
     * @param params 任务参数
     * @TODO 使用队列限制同时下载的任务数
     * @return 下载任务ID
     */
    String createTask(DownloadTaskParams params, int creator) throws NoSuchFileException;
}
