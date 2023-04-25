package com.sfc.archive.extractor;

import com.sfc.archive.ArchiveEventListenable;
import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.ArchiveParam;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public interface ArchiveExtractor extends Closeable, ArchiveEventListenable {

    /**
     * 访问压缩包内的文件
     * @param visitor  访问器
     * @return 压缩文件流，未关闭状态
     */
    ArchiveInputStream walk(ArchiveExtractorVisitor visitor) throws Exception;

    /**
     * 获取压缩包指定目录下的文件列表
     * @return  压缩文件信息列表
     */
    List<? extends ArchiveFile> listFiles() throws IOException, ArchiveException;

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

    /**
     * 当提取的文件不是本地文件系统的资源，需要先下载到本地文件系统时触发的事件回调
     * @param tempPathConsumer 创建的本地临时路径
     */
    void onResourceBeginFetch(Consumer<Path> tempPathConsumer);

    /**
     * 当提取的文件不是本地文件系统的资源，需要下载到本地文件系统完成时触发的事件回调
     * @param tempPathConsumer 创建的本地临时路径
     */
    void onResourceFinishFetch(Consumer<Path> tempPathConsumer);
}
