package com.sfc.archive.model;

/**
 * 文件传输回调接口。
 */
public interface FileTransferCallback {
    /**
     * 文件处理开始。
     *
     * @param archivePath 压缩包内路径
     */
    default void onFileStart(String archivePath) {
    }

    /**
     * 文件处理完成。
     *
     * @param archivePath 压缩包内路径
     */
    default void onFileComplete(String archivePath) {
    }

    /**
     * 传输进度回调。
     *
     * @param archivePath 压缩包内路径
     * @param loaded      已处理字节数
     * @param total       总字节数
     */
    default void onProgress(String archivePath, long loaded, long total) {
    }
}

