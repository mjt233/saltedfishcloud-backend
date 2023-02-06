package com.xiaotao.saltedfishcloud.download;

import com.xiaotao.saltedfishcloud.download.model.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.download.model.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.model.param.TaskType;
import org.springframework.data.domain.Page;

import java.io.IOException;

public interface DownloadService {

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
    String createTask(DownloadTaskParams params, int creator) throws IOException;
}
