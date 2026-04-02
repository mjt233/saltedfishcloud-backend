package com.xiaotao.saltedfishcloud.model.progress;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 文件复制进度记录
 */
@Data
@Accessors(chain = true)
public class CopyProgressRecord {

    /**
     * 当前正在复制的文件名（可能包含路径）
     */
    private String currentFile;

    /**
     * 当前正在操作的类型：file-文件, dir-目录
     */
    private String currentType;

    /**
     * 已复制的文件数量
     */
    private long copiedFileCount;

    /**
     * 已创建的目录数量
     */
    private long createdDirCount;

    /**
     * 已复制的文件总大小（字节）
     */
    private long copiedFileSize;

    /**
     * 是否已完成
     */
    private boolean completed;

    /**
     * 是否被中断
     */
    private boolean interrupted;

    /**
     * 错误信息，如果复制过程中发生错误
     */
    private String error;
}
