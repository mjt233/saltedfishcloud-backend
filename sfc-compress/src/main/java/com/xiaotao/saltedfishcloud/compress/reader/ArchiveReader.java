package com.xiaotao.saltedfishcloud.compress.reader;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface ArchiveReader {
    ArchiveInputStream getArchiveInputStream() throws IOException, ArchiveException;

    /**
     * 访问压缩包内的文件
     * @param visitor  访问器
     * @return 压缩包输入流，需要手动关闭
     */
    ArchiveInputStream walk(ArchiveReaderVisitor visitor) throws IOException, ArchiveException;

    /**
     * 获取压缩包指定目录下的文件列表
     * @param path  压缩包内部目录路径
     * @return  压缩文件信息列表
     */
    List<? extends CompressFile> listFiles(String path) throws IOException, ArchiveException;

    /**
     * 从压缩包内部获取指定文件的输入流
     * @param name  压缩包内的文件名
     * @return  文件输入流
     */
    InputStream getInputStream(String name) throws IOException, ArchiveException;

    /**
     * 按原目录提取所有文件到
     * @param dist 解压到的目录
     */
    void extractAll(Path dist) throws IOException, ArchiveException;
}
