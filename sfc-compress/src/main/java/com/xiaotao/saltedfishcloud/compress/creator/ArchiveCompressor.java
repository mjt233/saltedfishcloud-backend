package com.xiaotao.saltedfishcloud.compress.creator;

import java.io.Closeable;
import java.io.IOException;

public interface ArchiveCompressor extends Closeable {
    /**
     * 向压缩器中添加一个压缩实体资源，立即将内容添加到压缩包内
     * @param entry         压缩实体资源
     */
    void addFile(ArchiveResourceEntry entry) throws IOException;

    /**
     * 获取压缩器中已添加的文件数（目录不会被计入）
     * @return 文件数量
     */
    long getFileCount();

    /**
     * 标记压缩已完成，注意输出流不会在该步骤中关闭
     */
    void finish() throws IOException;
}
