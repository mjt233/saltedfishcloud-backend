package com.xiaotao.saltedfishcloud.compress.filesystem;

import org.apache.commons.compress.archivers.ArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface CompressFileSystem {
    ArchiveInputStream getArchiveInputStream() throws IOException;

    /**
     * 访问压缩包内的文件
     * @param visitor  访问器
     * @return 压缩包输入流，需要手动关闭
     */
    ArchiveInputStream walk(CompressFileSystemVisitor visitor) throws IOException;

    /**
     * 获取压缩包指定目录下的文件列表
     * @param path  压缩包内部目录路径
     * @return  压缩文件信息列表
     */
    List<? extends CompressFile> listFiles(String path) throws IOException;

    /**
     * 从压缩包内部获取指定文件的输入流
     * @param name  压缩包内的文件名
     * @return  文件输入流
     */
    InputStream getInputStream(String name) throws IOException;

    /**
     * 按原目录提取所有文件到
     * @param dist 解压到的目录
     */
    void extractAll(Path dist) throws IOException;
}
