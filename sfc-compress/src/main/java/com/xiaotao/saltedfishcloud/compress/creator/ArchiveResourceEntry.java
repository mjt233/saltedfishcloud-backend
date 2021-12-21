package com.xiaotao.saltedfishcloud.compress.creator;

import com.xiaotao.saltedfishcloud.compress.reader.CompressFile;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.io.InputStream;

public class ArchiveResourceEntry extends CompressFile implements InputStreamSource {
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
