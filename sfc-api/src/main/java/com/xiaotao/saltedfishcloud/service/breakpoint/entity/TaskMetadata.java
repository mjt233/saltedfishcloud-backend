package com.xiaotao.saltedfishcloud.service.breakpoint.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 断点续传任务元数据信息类
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskMetadata {
    protected String taskId;
    /**
     *  文件名
     */
    @NotBlank
    protected String fileName;
    /**
     *  文件长度
     */
    private long length;

    /**
     * 每个分块的大小（默认20MiB）
     * 最小20MiB，最大128MiB
     */
    @Min(20971520)
    @Max(134217728)
    private int chunkSize = 20971520;

    @Setter(AccessLevel.NONE)
    private int chunkCount = 0;

    private long lastChunkSize = 0;

    /**
     * 创建断点续传任务基本信息
     * @param taskId    任务ID
     * @param fileName  文件名
     * @param length    文件长度
     */
    public TaskMetadata(String taskId, @NotBlank String fileName, long length) {
        this.taskId = taskId;
        this.fileName = fileName;
        this.length = length;
    }

    /**
     * 更新块信息缓存，计算最后一块的大小，每块大小，分的块数
     */
    private void updateChunkInfo() {
        if (chunkCount == 0) {
            this.chunkCount = (int)Math.ceil((double)length / chunkSize);
            long t = length % chunkSize;
            lastChunkSize = t == 0 ? chunkSize : t;
        }
    }

    /**
     * 获取分块总数量
     */
    public int getChunkCount() {
        updateChunkInfo();
        return chunkCount;
    }

    /**
     * 获取最后一个分块的大小
     */
    public long getLastChunkSize() {
        updateChunkInfo();
        return lastChunkSize;
    }

    /**
     * 获取某个文件块的大小
     * @param part 文件块序号
     */
    public long getPartSize(int part) {
        updateChunkInfo();
        if (part > chunkCount) {
            throw new IndexOutOfBoundsException();
        }
        if (part == chunkCount) {
            return lastChunkSize;
        } else {
            return chunkSize;
        }
    }

}
