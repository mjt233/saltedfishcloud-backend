package com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl;

import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;
import com.xiaotao.saltedfishcloud.service.breakpoint.PartParser;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.utils.TaskStorePath;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MergeInputStream;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MultipleFileMergeInputStreamGenerator;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 断点续传任务管理器，管理任务的创建，查询，删除和文件块的存储
 */
@Slf4j
public class DefaultTaskManager implements TaskManager {


    /**
     * 创建断点续传任务
     * @param info  任务元数据
     * @return      创建成功后的任务ID
     * @throws IOException 任务数据存储目录无法写入
     */
    @Override
    public String createTask(TaskMetadata info) throws IOException {
        var id = UUID.randomUUID().toString();
        info.setTaskId(id);
        var taskDir = TaskStorePath.getRoot(id);
        Files.createDirectories(taskDir);
        Files.write(TaskStorePath.getMetadata(id), MapperHolder.mapper.writeValueAsBytes(info));

        return id;
    }

    /**
     * 查询任务信息
     * @param id 任务ID
     * @return 任务信息，若任务不存在则返回Null
     * @throws IOException 目录读取出错
     */
    @Override
    public TaskMetadata queryTask(String id) throws IOException {
        var metadataPath = TaskStorePath.getMetadata(id);
        if (!Files.exists(metadataPath)) {
            throw new TaskNotFoundException(id);
        }

        return MapperHolder.mapper.readValue(Files.readAllBytes(metadataPath), TaskMetadata.class);
    }

    /**
     * 清理指定的任务数据
     * @param id    任务ID
     * @throws IOException 目录不可写或任务不存在
     */
    @Override
    public void clear(String id) throws IOException {
        var taskPath = TaskStorePath.getRoot(id);
        if (!Files.exists(taskPath)) {
            throw new TaskNotFoundException(id);
        }
        FileUtils.delete(taskPath);
    }

    /**
     * 保存部分的断点续传任务文件片段
     * @param id        任务ID
     * @param part      文件块编号（从1开始）
     * @param stream    文件流
     */
    @Override
    public void save(String id, String part, InputStream stream) throws IOException {
        var root = TaskStorePath.getRoot(id);
        if (!Files.exists(root)) {
            throw new TaskNotFoundException(id);
        }
        var parts = PartParser.parse(part);
        var taskInfo = queryTask(id);
        for (int i : parts) {
            var size = taskInfo.getPartSize(i);
            var out = Files.newOutputStream(TaskStorePath.getPartFile(id, i));
            long l = StreamUtils.copyRange(stream, out, 0, size - 1);
            log.debug("写入断点续传文件块，文件名：{} 编号：{}  大小：{}",taskInfo.getFileName() ,i, l);
            out.close();
        }
        stream.close();
    }

    @Override
    public List<Integer> getFinishPart(String id) throws IOException {
        return Files.list(TaskStorePath.getRoot(id))
                .filter(e -> e.toString().endsWith(".part"))
                .map(e -> Integer.parseInt(e.getFileName().toString().replaceAll(".part", "")))
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public boolean isFinish(String id) throws IOException {
        try {
            TaskMetadata taskMetadata = queryTask(id);
            return getFinishPart(id).size() == taskMetadata.getChunkCount();
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public MergeInputStream getMergeInputStream(String id) throws IOException {
        if (!this.isFinish(id)) {
            throw new IllegalStateException("断点续传任务未完成,文件块不完整");
        }


        List<Integer> finishPart = getFinishPart(id);
        Path[] paths = new Path[finishPart.size()];
        for (Integer integer : finishPart) {
            paths[integer - 1] = TaskStorePath.getPartFile(id, integer);
        }
        return new MergeInputStream(new MultipleFileMergeInputStreamGenerator(paths));
    }
}
