package com.xiaotao.saltedfishcloud.service.file.store.attach;

import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.StreamCopyResult;
import com.xiaotao.saltedfishcloud.utils.StreamUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/**
 * 附属存储
 */
public interface AttachStorage {

    /**
     * 在存储中获取文件资源
     * @param path  文件路径
     * @return  不存在则返回null
     */
    Optional<Resource> getFile(String path) throws IOException;

    /**
     * 保存文件到附属存储（如果上级目录不存在会自动创建）
     * @param path 文件的保存路径
     * @param outputStreamConsumer  一个输出流消费者，负责将文件数据写入到提供的输出流中。外部不需要关闭流。
     * @return  保存的文件大小
     */
    long saveFile(String path, OutputStreamConsumer<OutputStream> outputStreamConsumer) throws IOException;

    /**
     * 保存文件到附属存储（如果上级目录不存在会自动创建）
     * @param path  文件的保存路径
     * @param resource  文件资源
     * @return  保存的文件大小
     */
    default long saveFile(String path, Resource resource) throws IOException {
        return saveFile(path, os -> {
            try (InputStream is = resource.getInputStream()) {
                // 不计算md5了
                long size = StreamUtils.copyStream(is, os);
                return new StreamCopyResult(size, null);
            }
        });
    }


    /**
     * 列出目录下的文件列表
     * @param path 目录路径
     * @return 目录下的文件列表。如果目录不存在会返回null
     */
    Optional<List<FileInfo>> listFiles(String path) throws IOException;

    /**
     * 检测路径是否存在
     * @param path  待检测的路径
     */
    boolean exist(String path) throws IOException;

    /**
     * 删除文件或目录。<br>
     * <strong>注意：如果被删除的路径是目录，则会连同目录及其子目录下的所有内容一并删除</strong>
     * @param path  待删除的路径
     */
    void delete(String path) throws IOException;

    /**
     * 创建文件夹（如果父级不存在则一并创建）
     * @param path  待创建的文件夹
     */
    void mkdir(String path) throws IOException;
}
