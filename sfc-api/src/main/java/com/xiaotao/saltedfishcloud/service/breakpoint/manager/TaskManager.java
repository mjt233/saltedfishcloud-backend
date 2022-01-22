package com.xiaotao.saltedfishcloud.service.breakpoint.manager;

import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MergeInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface TaskManager {

    /**
     * 创建断点续传任务
     * @param info  任务元数据
     * @return      创建成功后的任务ID
     */
    String createTask(TaskMetadata info) throws IOException;

    /**
     * 查询任务信息
     * @param id 任务ID
     * @return 任务信息，若任务不存在则返回Null
     */
    TaskMetadata queryTask(String id) throws IOException;

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

    /**
     * 获取任务已经完成的块编号
     * @param id 任务ID
     * @return  块编号集合
     * @throws IOException  IO出错
     */
    List<Integer> getFinishPart(String id) throws IOException;

    /**
     * 判断任务是否完成
     * @param id 任务ID
     * @return      完成true，否则false
     * @throws IOException IO出错
     */
    boolean isFinish(String id) throws IOException;

    /**
     * 获取断点续传任务的文件合并输入流。
     * @param id    任务ID
     * @return      合并输入流
     * @throws IOException IO错误
     * @throws IllegalStateException 任务未完成
     * @throws com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException 任务ID不存在
     */
    MergeInputStream getMergeInputStream(String id) throws IOException;
}
