package com.sfc.archive;

import com.sfc.archive.model.ArchiveFile;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 压缩文件处理事件监听器
 */
public interface ArchiveHandleEventListener {
    /**
     * 文件开始处理回调，表示文件开始压缩或开始解压缩
     * @param consumer 开始处理的文件消费函数
     */
    default void onFileBeginHandle(Consumer<ArchiveFile> consumer) {

    }

    /**
     * 文件完成处理回调，表示文件完成压缩或完成解压缩
     * @param consumer  文件压缩完成的消费回调，参数1完成压缩的文件信息，参数2为耗时（ms）
     */
    default void onFileFinishCompress(BiConsumer<ArchiveFile, Long> consumer) {

    }

    /**
     * 目录创建回调
     * @param consumer  创建的目录消费函数
     */
    default void onDirCreate(Consumer<ArchiveFile> consumer) {

    }

    /**
     * 整个压缩文件处理任务开始时的回调，表示文件压缩或文件解压缩任务开始执行
     * @param runnable 事件处理回调函数，借用Runnable无参数无返回的函数时接口
     */
    default void onBeginHandle(Runnable runnable) {

    }

    /**
     * 整个文件处理任务完成时的回调，表示文件压缩或文件解压缩任务执行完成执行
     * @param consumer  回调处理函数，参数为整个任务的处理耗时
     */
    default void onFinishCompress(Consumer<Long> consumer) {

    }

    /**
     * 出现错误时的回调
     * @param consumer 错误处理器，参数1为正在进行压缩/解压缩时的文件，当然也可能是null，说明异常不是发生在对某个文件的处理过程中。参数2为异常信息。
     */
    default void onError(BiConsumer<ArchiveFile, Throwable> consumer) {

    }
}
