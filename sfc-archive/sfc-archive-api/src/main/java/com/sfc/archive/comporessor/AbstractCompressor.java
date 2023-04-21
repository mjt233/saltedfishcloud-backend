package com.sfc.archive.comporessor;

import com.sfc.archive.ArchiveHandleEventListener;
import com.sfc.archive.model.ArchiveFile;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractCompressor implements ArchiveCompressor {
    private ArchiveOutputStream out;
    private long count = 0;

    protected Collection<ArchiveHandleEventListener> listeners = new ArrayList<>();

    /**
     * 开始压缩时间
     */
    private long taskBeginTime;

    /**
     * 压缩完成时间
     */
    private long taskEndTime;

    /**
     * 初始化压缩输出流
     */
    protected abstract ArchiveOutputStream initArchiveOutputStream();

    /**
     * 包装压缩实体
     * @param file  提供的基本压缩信息
     * @return      包装后的压缩实体信息类
     */
    protected abstract ArchiveEntry wrapEntry(ArchiveFile file);

    @Override
    public void addEventListener(ArchiveHandleEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void addFile(ArchiveResourceEntry entry) throws IOException {
        if (taskBeginTime == 0) {
            taskBeginTime = System.currentTimeMillis();
            listeners.forEach(ArchiveHandleEventListener::onBeginHandle);
        }

        try {
            if (out == null) {
                out = initArchiveOutputStream();
            }

            ArchiveEntry ae = wrapEntry(entry);

            long fileBeginTime = System.currentTimeMillis();
            out.putArchiveEntry(ae);
            if (!entry.isDirectory()) {
                listeners.forEach(listener -> listener.onFileBeginHandle(entry));
                try(InputStream in = entry.getInputStream()) {
                    StreamUtils.copy(in, out);
                    listeners.forEach(listener -> listener.onFileFinishCompress(entry, System.currentTimeMillis() - fileBeginTime));
                } catch (IOException e) {
                    out.close();
                    throw e;
                }
            } else {
                listeners.forEach(listener -> listener.onDirCreate(entry));
            }

            out.closeArchiveEntry();
            if (!entry.isDirectory()) {
                count++;
            }
        } catch (Throwable throwable) {
            listeners.forEach(listener -> listener.onError(entry, throwable));
            throw new IOException(throwable);
        }
    }

    @Override
    public long getFileCount() {
        return count;
    }

    @Override
    public void finish() throws IOException {
        out.finish();
        if (taskEndTime == 0) {
            taskEndTime = System.currentTimeMillis();
            listeners.forEach(listener -> listener.onFinishCompress(taskEndTime - taskBeginTime));
        }
    }

    public void close() throws IOException {
        try {
            out.closeArchiveEntry();
        } catch (IOException ignore) { }
        if (out != null) {
            out.finish();
            out.close();
        }
    }
}
