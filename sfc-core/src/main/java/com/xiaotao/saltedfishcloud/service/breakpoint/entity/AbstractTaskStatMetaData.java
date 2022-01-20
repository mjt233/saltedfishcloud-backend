package com.xiaotao.saltedfishcloud.service.breakpoint.entity;

import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.utils.TaskStorePath;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MergeInputStream;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Slf4j
public abstract class AbstractTaskStatMetaData extends TaskMetadata {


    protected List<Integer> finishPart;

    /**
     * @param data 基础的任务元数据
     * @throws TaskNotFoundException 任务ID不存在
     */
    public AbstractTaskStatMetaData(TaskMetadata data) {
        super(data.getTaskId(), data.getFileName(), data.getLength());
        if (!Files.exists(TaskStorePath.getRoot(data.getTaskId()))) {
            throw new TaskNotFoundException(data.getTaskId());
        }
    }


    /**
     * 更新任务的完成状态信息。
     * @TODO 通过文件大小进行数据校验
     */
    public abstract void fresh() throws IOException;

    public abstract List<Integer> getFinishPart() throws IOException;

    /**
     * 返回该分块任务是否已完成
     */
    public abstract boolean isFinish();

    public abstract MergeInputStream getMergeInputStream() throws IOException;
}
