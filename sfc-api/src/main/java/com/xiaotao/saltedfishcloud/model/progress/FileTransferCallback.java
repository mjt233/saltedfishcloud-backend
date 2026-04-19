package com.xiaotao.saltedfishcloud.model.progress;

/**
 * 文件传输进度回调接口
 */
public interface FileTransferCallback {

    /**
     * 当开始复制一个文件时调用
     * @param record 文件信息
     */
    default void onFileStart(FileTransferItem record) {}

    /**
     * 当完成复制一个文件时调用
     * @param record 文件信息
     */
    default void onFileComplete(FileTransferItem record) {}

    /**
     * 额外的操作事件
     */
    default void onAdditionalEvent(CopyProgressEvent event) {}

    /**
     * 当开始创建一个目录时调用
     * @param dirPath 目录路径
     */
    default void onDirStart(String dirPath) {}

    /**
     * 当完成创建一个目录时调用
     * @param dirPath 目录路径
     */
    default void onDirComplete(String dirPath) {}

    /**
     * 获取当前进度记录
     * @return 进度记录
     */
    CopyProgressRecord getProgressRecord();

    /**
     * 检查是否应该中断复制操作
     * @return true 表示应该中断
     */
    default boolean shouldInterrupt() {
        return getProgressRecord().isInterrupted();
    }

    /**
     * 中断复制操作
     */
    default void interrupt() {
        getProgressRecord().setInterrupted(true);
    }
}
