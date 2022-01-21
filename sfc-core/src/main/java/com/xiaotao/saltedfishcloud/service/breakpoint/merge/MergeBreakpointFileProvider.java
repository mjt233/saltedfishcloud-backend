package com.xiaotao.saltedfishcloud.service.breakpoint.merge;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MergeBreakpointFileProvider {

    /**
     * 获取合并断点续传文件块的MultipartFile对象
     * @param taskId    任务ID
     * @return          合并断点续传文件块的MultipartFile
     */
    MultipartFile getFile(String taskId) throws IOException;
}
