package com.xiaotao.saltedfishcloud.utils.compress.reader;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface ArchiveReader extends Closeable {

    /**
     * 访问压缩包内的文件
     * @param visitor  访问器
     */
    ArchiveInputStream walk(ArchiveReaderVisitor visitor) throws Exception;

    /**
     * 获取压缩包指定目录下的文件列表
     * @return  压缩文件信息列表
     */
    List<? extends CompressFile> listFiles() throws IOException, ArchiveException;

    /**
     * 从压缩包内部获取指定文件的输入流
     * @param fullPath  压缩包内的完整路径文件名
     * @return  文件输入流
     */
    InputStream getInputStream(String fullPath) throws IOException, ArchiveException;

    /**
     * 按原目录提取所有文件到
     * @param dist 解压到的目录
     */
    void extractAll(Path dist) throws IOException, ArchiveException;
}
