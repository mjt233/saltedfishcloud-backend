package com.sfc.archive.composer;

import com.sfc.archive.ArchiveHandleEventListener;
import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.comporessor.ArchiveResourceEntry;
import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.ArchiveParam;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 抽象压缩器，基于apache-commons-compress的压缩工具，使用addFile添加完文件后即可进行压缩。<br>
 * 默认实现了事件处理、进度统计、
 *
 */
@Slf4j
public abstract class AbstractCompressor implements ArchiveCompressor {
    private ArchiveOutputStream archiveOutputStream;
    private long count = 0;

    /**
     * 监听器列表
     */
    protected Collection<ArchiveHandleEventListener> listeners = new ArrayList<>();

    /**
     * 待压缩的资源实体列表
     */
    private final List<ArchiveResourceEntry> entryList = new ArrayList<>();


    /**
     * 开始压缩时间
     */
    private long taskBeginTime;

    /**
     * 压缩完成时间
     */
    private long taskEndTime;

    /**
     * 总需要压缩的量
     */
    @Getter
    private long total;

    /**
     * 已完成压缩的量
     */
    @Getter
    private long loaded;

    protected ArchiveParam archiveParam;
    protected OutputStream originOutput;

    /**
     * 当前正在被压缩的文件的输入流
     */
    private InputStream curInputStream;


    public AbstractCompressor(ArchiveParam archiveParam, OutputStream originOutput) {
        this.archiveParam = archiveParam;
        this.originOutput = originOutput;
    }

    @Override
    public ArchiveParam getParam() {
        return archiveParam;
    }

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
    public void start() throws IOException {
        if (archiveOutputStream != null) {
            throw new IllegalStateException("压缩器已经执行过start了");
        }
        archiveOutputStream = initArchiveOutputStream();

        taskBeginTime = System.currentTimeMillis();
        listeners.forEach(ArchiveHandleEventListener::onBegin);

        for (ArchiveResourceEntry entry : entryList) {
            compressFile(entry);
        }

        if (taskEndTime == 0) {
            taskEndTime = System.currentTimeMillis();
            listeners.forEach(listener -> listener.onFinish(taskEndTime - taskBeginTime));
        }
    }

    @Override
    public void addEventListener(ArchiveHandleEventListener listener) {
        listeners.add(listener);
    }

    private void compressFile(ArchiveResourceEntry entry) throws IOException {
        try {
            ArchiveEntry ae = wrapEntry(entry);

            long fileBeginTime = System.currentTimeMillis();
            archiveOutputStream.putArchiveEntry(ae);
            if (!entry.isDirectory()) {
                listeners.forEach(listener -> listener.onFileBeginHandle(entry));
                try(InputStream in = entry.getInputStream()) {
                    curInputStream = in;
                    loaded += StreamUtils.copy(in, archiveOutputStream);
                    listeners.forEach(listener -> listener.onFileFinishHandle(entry, System.currentTimeMillis() - fileBeginTime));
                } catch (IOException e) {
                    archiveOutputStream.close();
                    throw e;
                }
            } else {
                listeners.forEach(listener -> listener.onDirCreate(entry));
            }

            archiveOutputStream.closeArchiveEntry();
            if (!entry.isDirectory()) {
                count++;
            }
        } catch (Throwable throwable) {
            listeners.forEach(listener -> listener.onError(entry, throwable));
            throw new IOException(throwable);
        }
    }

    @Override
    public void addFile(ArchiveResourceEntry entry) {
        entryList.add(entry);
        if (!entry.isDirectory()) {
            this.total += entry.getSize();
        }
    }

    @Override
    public long getFileCount() {
        return count;
    }

    @Override
    public void close() throws IOException {

        if (archiveOutputStream != null) {
            curInputStream.close();
            synchronized (archiveOutputStream) {
                try {
                    archiveOutputStream.closeArchiveEntry();
                } catch (IOException ignore) { }
                try {
                    archiveOutputStream.finish();
                } finally {
                    archiveOutputStream.close();
                }

            }
        }
        if (originOutput != null) {
            originOutput.close();
        }
    }
}
