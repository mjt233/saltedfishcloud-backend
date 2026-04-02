package com.xiaotao.saltedfishcloud.model.progress;

import java.io.IOException;

/**
 * 文件复制进度回调接口
 */
public interface CopyProgressCallback {

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

    /**
     * 设置错误信息
     * @param error 错误信息
     */
    default void setError(String error) {
        getProgressRecord().setError(error);
    }

    /**
     * 标记为已完成
     */
    default void complete() {
        getProgressRecord().setCompleted(true);
    }

    /**
     * 当复制过程中发生错误时调用
     * @param error 错误信息
     * @param e 异常对象
     */
    default void onError(String error, IOException e) {
        setError(error);
    }
}
