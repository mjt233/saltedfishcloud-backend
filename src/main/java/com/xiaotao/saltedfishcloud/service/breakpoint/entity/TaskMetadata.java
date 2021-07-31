package com.xiaotao.saltedfishcloud.service.breakpoint.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jdk.nashorn.internal.objects.annotations.Getter;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

/**
 * 断点续传任务元数据信息类
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskMetadata {
    private String taskId;
    /**
     *  文件名
     */
    @NotBlank
    private String fileName;
    /**
     *  文件长度
     */
    private int length;

    /**
     * 每个分块的大小（默认2MiB）
     */
    private int chunkSize = 2097152;

    /**
     * 获取整个任务的总分块数量
     */
    @JsonInclude
    public int getChunkCount() {
        return (int)Math.ceil((double)length / chunkSize);
    }

}
