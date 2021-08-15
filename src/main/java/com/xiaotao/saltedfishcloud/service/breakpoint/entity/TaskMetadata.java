package com.xiaotao.saltedfishcloud.service.breakpoint.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

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
     * 每个分块的大小（默认2MiB）
     * 最小2MiB，最大64MiB
     */
    @Min(2097152)
    @Max(67108864)
    private int chunkSize;

    @Setter(AccessLevel.NONE)
    private int chunkCount = 0;

    private long lastChunkSize = 0;


    public TaskMetadata(String taskId, @NotBlank String fileName, long length) {
        this.taskId = taskId;
        this.fileName = fileName;
        setLength(length);
    }

    public void setLength(long length) {
        this.length = length;
        this.chunkCount = (int)Math.ceil((double)length / chunkSize);
        long t = length % chunkSize;
        lastChunkSize = t == 0 ? chunkSize : t;
    }
    /**
     * 获取某个文件块的大小
     * @param part 文件块序号
     */
    public long getPartSize(int part) {
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
