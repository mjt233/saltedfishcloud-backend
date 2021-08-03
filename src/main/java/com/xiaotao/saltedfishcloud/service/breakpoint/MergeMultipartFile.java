package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskStatMetadata;
import lombok.var;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MergeMultipartFile implements MultipartFile {
    private final TaskStatMetadata taskData;
    public MergeMultipartFile(TaskStatMetadata data) {
        if (!data.isFinish()) {
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
        var in = getInputStream();
        var r = in.read(ret);
        in.close();
        if (r != getSize()) {
            throw new IOException("不完整的流");
        }
        return ret;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return taskData.getMergeInputStream();
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.copy(getInputStream(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
