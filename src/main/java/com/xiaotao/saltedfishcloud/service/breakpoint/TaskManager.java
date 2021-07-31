package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 断点续传任务管理器，管理
 */
@Slf4j
@Component
public class TaskManager  {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 获取任务数据文件夹路径
     * @param id 任务ID
     */
    private Path getTaskDir(String id) {
        return Paths.get(PathUtils.getTempDirectory() + "/xyy/" + id);
    }

    /**
     * 创建断点续传任务
     * @param info  任务元数据
     * @return      创建成功后的任务ID
     * @throws IOException 任务数据存储目录无法写入
     */
    public String createTask(TaskMetadata info) throws IOException {
        var id = UUID.randomUUID().toString();
        info.setTaskId(id);
        var taskDir = getTaskDir(id);
        Files.createDirectories(taskDir);
        Files.write(Paths.get(taskDir + "/metadata.json"), mapper.writeValueAsBytes(info));

        log.debug("Create Breakpoint Task：" + taskDir);
        return id;
    }

    /**
     * 查询任务信息
     * @param id 任务ID
     * @return 任务信息
     * @throws IOException 任务不存在或目录不可读
     */
    public TaskMetadata queryTask(String id) throws IOException {
        var metadataPath = Paths.get(getTaskDir(id) +"/metadata.json");
        if (!Files.exists(metadataPath)) {
            return null;
        }

        return mapper.readValue(Files.readAllBytes(metadataPath), TaskMetadata.class);
    }

    /**
     * 清理指定的任务数据
     * @param id    任务ID
     * @throws IOException 目录不可写或任务不存在
     */
    public void clear(String id) throws IOException {
        var taskPath = getTaskDir(id);
        Files.list(taskPath).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        Files.delete(taskPath);
    }
}
