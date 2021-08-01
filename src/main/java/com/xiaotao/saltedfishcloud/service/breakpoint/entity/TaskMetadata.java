package com.xiaotao.saltedfishcloud.service.breakpoint.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    @Setter(AccessLevel.NONE)
    private int chunkSize = 2097152;

    @Setter(AccessLevel.NONE)
    private int chunkCount = 0;

    public void setLength(int length) {
        this.length = length;
        this.chunkCount = (int)Math.ceil((double)length / chunkSize);
    }

    /**
     * 取最后一个文件块的大小
     */
    public int getLastChunkSize() {
        int res = length % chunkSize;
        return res == 0 ? chunkSize : res;
    }

    /**
     * 获取某个文件块的大小
     * @param part 文件块序号
     */
    public int getPartSize(int part) {
        if (part > chunkCount) {
            throw new IndexOutOfBoundsException();
        }
        if (part == chunkCount) {
            return getLastChunkSize();
        } else {
            return chunkSize;
        }
    }

}
