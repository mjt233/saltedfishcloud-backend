package com.sfc.archive.comporessor;

import com.sfc.archive.model.ArchiveFile;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.io.InputStream;

/**
 * 压缩资源实体，表示一个输入压缩包的被压缩文件实体，或者是一个从压缩包中读取出来的被解压文件实体。
 */
public class ArchiveResourceEntry extends ArchiveFile implements InputStreamSource {
    private final String path;
    private final long size;
    private final InputStreamSource streamSource;

    /**
     * 创建一个压缩资源实体
     * @param path          完整压缩包路径
     * @param size          文件大小
     * @param streamSource  输入流资源
     */
    public ArchiveResourceEntry(String path, long size, InputStreamSource streamSource) {
        this.path = path;
        this.size = size;
        this.streamSource = streamSource;
    }


    @Override
    public long getSize() {
        return this.size;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return streamSource.getInputStream();
    }
}
