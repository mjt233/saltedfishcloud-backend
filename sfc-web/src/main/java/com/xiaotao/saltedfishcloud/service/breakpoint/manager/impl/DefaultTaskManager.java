package com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.utils.PartParser;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.utils.TaskStorePath;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskStatMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

/**
 * 断点续传任务管理器，管理任务的创建，查询，删除和文件块的存储
 */
@Slf4j
public class DefaultTaskManager implements TaskManager {
    private final ObjectMapper mapper = new ObjectMapper();


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
        Files.write(TaskStorePath.getMetadata(id),mapper.writeValueAsBytes(info));

        log.debug("Create Breakpoint Task：" + taskDir);
        return id;
    }

    /**
     * 查询任务信息
     * @param id 任务ID
     * @return 任务信息，若任务不存在则返回Null
     * @throws IOException 目录读取出错
     */
    @Override
    public TaskStatMetadata queryTask(String id) throws IOException {
        var metadataPath = TaskStorePath.getMetadata(id);
        if (!Files.exists(metadataPath)) {
            throw new TaskNotFoundException(id);
        }

        var basicInfo = mapper.readValue(Files.readAllBytes(metadataPath), TaskMetadata.class);
        return new TaskStatMetadata(basicInfo);
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
        Files.list(taskPath).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        Files.delete(taskPath);
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
            StreamUtils.copyRange(stream, out, 0, size - 1);
            out.close();
        }
        stream.close();
    }
}
