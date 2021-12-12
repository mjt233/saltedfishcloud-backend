package com.xiaotao.saltedfishcloud.compress.creator;

import com.xiaotao.saltedfishcloud.compress.reader.CompressFile;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractCompressor implements ArchiveCompressor {
    private ArchiveOutputStream out;

    /**
     * 初始化压缩输出流
     */
    protected abstract ArchiveOutputStream initArchiveOutputStream();

    /**
     * 包装压缩实体
     * @param file  提供的基本压缩信息
     * @return      包装后的压缩实体信息类
     */
    protected abstract ArchiveEntry wrapEntry(CompressFile file);

    @Override
    public void addFile(ArchiveResourceEntry entry) throws IOException {
        if (out == null) {
            out = initArchiveOutputStream();
        }

        ArchiveEntry ae = wrapEntry(entry);
        out.putArchiveEntry(ae);
        if (!entry.isDirectory()) {
            try(InputStream in = entry.getInputStream()) {
                StreamUtils.copy(in, out);
            } catch (IOException e) {
                out.close();
                throw e;
            }
        }
        out.closeArchiveEntry();
    }

    @Override
    public void finish() throws IOException {
        out.finish();
    }

    public void close() throws IOException {
        out.finish();
        out.close();
    }
}
