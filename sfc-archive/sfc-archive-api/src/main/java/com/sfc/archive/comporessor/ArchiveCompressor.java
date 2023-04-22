package com.sfc.archive.comporessor;

import com.sfc.archive.ArchiveEventListenable;
import com.sfc.archive.model.ArchiveParam;

import java.io.Closeable;
import java.io.IOException;

public interface ArchiveCompressor extends Closeable, ArchiveEventListenable {
    /**
     * 开始压缩
     */
    void start() throws IOException;

    /**
     * 获取压缩参数
     * @return 原始参数
     */
    ArchiveParam getParam();

    /**
     * 向压缩器中添加一个压缩实体资源，将内容添加到压缩包内
     * @param entry         压缩实体资源
     */
    void addFile(ArchiveResourceEntry entry);

    /**
     * 获取待压缩的文件总大小
     * @return  Byte
     */
    long getTotal();

    /**
     * 获取待压缩文件中已完成压缩的大小
     * @return Byte
     */
    long getLoaded();

    /**
     * 获取压缩器中已添加的文件数（目录不会被计入）
     * @return 文件数量
     */
    long getFileCount();

    /**
     * 关闭时会连同关联的输出流也一并关闭
     */
    @Override
    void close() throws IOException;
}
