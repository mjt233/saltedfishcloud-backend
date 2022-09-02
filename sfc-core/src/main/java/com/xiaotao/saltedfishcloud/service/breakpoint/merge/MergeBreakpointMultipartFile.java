package com.xiaotao.saltedfishcloud.service.breakpoint.merge;

import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MergeBreakpointMultipartFile implements MultipartFile {
    private final TaskMetadata taskData;
    private final TaskManager taskManager;
    public MergeBreakpointMultipartFile(TaskMetadata data, TaskManager manager) throws IOException {
        taskManager = manager;
        if(!manager.isFinish(data.getTaskId())) {
            throw new IllegalStateException("任务未完成，无法合并");
        }
        this.taskData = data;
    }
    @Override
    public String getName() {
        return taskData.getFileName();
    }

    @Override
    public String getOriginalFilename() {
        return taskData.getFileName();
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public long getSize() {
        return taskData.getLength();
    }

    @Override
    public byte[] getBytes() throws IOException {
        byte[] ret = new byte[Math.toIntExact(getSize())];
        InputStream in = getInputStream();
        int r = in.read(ret);
        in.close();
        if (r != getSize()) {
            throw new IOException("不完整的流");
        }
        return ret;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return taskManager.getMergeInputStream(taskData.getTaskId());
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.copy(getInputStream(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
