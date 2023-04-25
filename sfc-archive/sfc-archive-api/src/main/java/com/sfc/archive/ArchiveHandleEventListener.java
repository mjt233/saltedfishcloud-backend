package com.sfc.archive;

import com.sfc.archive.model.ArchiveFile;

/**
 * 压缩文件处理事件监听器
 */
public interface ArchiveHandleEventListener {
    /**
     * 文件开始处理回调，表示文件开始压缩或开始解压缩
     * @param archiveFile 开始处理的文件
     */
    default void onFileBeginHandle(ArchiveFile archiveFile) {

    }

    /**
     * 文件完成处理回调，表示文件完成压缩或完成解压缩
     * @param archiveFile 处理完成的文件
     * @param consumeTime 为耗时（ms）
     */
    default void onFileFinishHandle(ArchiveFile archiveFile, long consumeTime) {

    }

    /**
     * 目录创建回调
     * @param archiveFile 开始处理的文件
     */
    default void onDirCreate(ArchiveFile archiveFile) {

    }

    /**
     * 整个压缩文件处理任务开始时的回调，表示文件压缩或文件解压缩任务开始执行
     * @param runnable 事件处理回调函数，借用Runnable无参数无返回的函数时接口
     */
    default void onBegin() {

    }

    /**
     * 整个文件处理任务完成时的回调，表示文件压缩或文件解压缩任务执行完成执行
     * @param consumeTime  整个任务的处理耗时(ms)
     */
    default void onFinish(long consumeTime) {

    }

    /**
     * 出现错误时的回调
     * @param archiveFile 正在进行压缩/解压缩时的文件，当然也可能是null，说明异常不是发生在对某个文件的处理过程中
     * @param throwable   异常信息。
     */
    default void onError(ArchiveFile archiveFile, Throwable throwable) {

    }
}
