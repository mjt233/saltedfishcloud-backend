package com.xiaotao.saltedfishcloud.task;

import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressCallback;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressRecord;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEvent;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.DiskFileSystemUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文件复制异步任务
 */
@Slf4j
public class FileCopyAsyncTask implements AsyncTask {

    private final String originParams;
    private final SimpleFileTransferParam param;

    @Setter
    private DiskFileSystem diskFileSystem;

    private PrintWriter logWriter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private final AtomicReference<Thread> executeThread = new AtomicReference<>();
    private CopyProgressCallback progressCallback;

    private final ProgressRecord progressRecord = new ProgressRecord();

    public FileCopyAsyncTask(String originParams, SimpleFileTransferParam param) {
        this.originParams = originParams;
        this.param = param;
    }

    private void log(String message) {
        if (logWriter != null) {
            logWriter.println(message);
            logWriter.flush();
        }
    }

    private List<String> getSourcePaths() {
        if (CollectionUtils.isEmpty(param.getFiles())) {
            return List.of(param.getSourcePath());
        } else {
            return param.getFiles().stream().map(f -> StringUtils.appendPath(param.getSourcePath(), f)).toList();
        }
    }

    private void initCallback() {
        // 创建自定义的复制进度回调
        this.progressCallback = new CopyProgressCallback() {
            private final CopyProgressRecord record = new CopyProgressRecord();

            @Override
            public void onFileStart(FileTransferItem item) {
                String fileName = item.getFileInfo() != null ? item.getFileInfo().getName() : "未知文件";
                log(item.getFrom() + " -> " + item.getTo());
                record.setCurrentFile(fileName);
                record.setCurrentType("file");
            }

            @Override
            public void onFileComplete(FileTransferItem item) {
                if (Boolean.TRUE.equals(item.getIsSkip())) {
                    log("跳过: " + (item.getFileInfo() != null ? item.getFileInfo().getName() : "未知文件"));
                }
                if (item.getFileInfo() != null && item.getFileInfo().isFile()) {
                    progressRecord.setLoaded(progressRecord.getLoaded() + item.getFileInfo().getSize());
                    CopyProgressRecord callbackProgressRecord = progressCallback.getProgressRecord();
                    callbackProgressRecord.setCopiedFileSize(progressRecord.getLoaded());
                    callbackProgressRecord.setCopiedFileCount(callbackProgressRecord.getCopiedFileCount() + 1);
                }
            }

            @Override
            public void onDirStart(String dirPath) {
                record.setCurrentFile(dirPath);
                record.setCurrentType("dir");
            }

            @Override
            public void onDirComplete(String dirPath) {
                record.setCreatedDirCount(record.getCreatedDirCount() + 1);
            }

            @Override
            public void onAdditionalEvent(CopyProgressEvent event) {
                if (event.getMessage() != null) {
                    log(event.getMessage());
                }
            }

            @Override
            public CopyProgressRecord getProgressRecord() {
                return record;
            }

            @Override
            public boolean shouldInterrupt() {
                return interrupted.get();
            }
        };
    }

    private void checkTotalSize() throws IOException {
        for (String sourcePath : getSourcePaths()) {
            DiskFileSystemUtils.walk(diskFileSystem, param.getSourceUid(), sourcePath, new FileVisitor<>() {
                @NotNull
                @Override
                public FileVisitResult preVisitDirectory(FileInfo dir, @NotNull BasicFileAttributes attrs) throws IOException {
                    if (dir.getMountId() != null) {
                        log("发现子挂载点，跳过: " + StringUtils.appendPath(dir.getPath(), dir.getName()));
                        return FileVisitResult.SKIP_SIBLINGS;
                    }
                    log("发现子目录: " + StringUtils.appendPath(dir.getPath(), dir.getName()));
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult visitFile(FileInfo file, @NotNull BasicFileAttributes attrs) throws IOException {
                    if (file.isFile()) {
                        if (progressRecord.getTotal() < 0) {
                            progressRecord.setTotal(0);
                        }
                        progressRecord.setTotal(progressRecord.getTotal() + file.getSize());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult visitFileFailed(FileInfo file, @NotNull IOException exc) throws IOException {
                    return FileVisitResult.TERMINATE;
                }

                @NotNull
                @Override
                public FileVisitResult postVisitDirectory(FileInfo dir, @Nullable IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Override
    public void execute(OutputStream logOutputStream) {
        if (running.get()) {
            throw new IllegalStateException("任务正在执行中，不能重复执行");
        }
        running.set(true);
        executeThread.set(Thread.currentThread());
        logWriter = new PrintWriter(new OutputStreamWriter(logOutputStream, StandardCharsets.UTF_8), true);

        try {
            if (CollectionUtils.isEmpty(param.getFiles())) {
                log("开始文件复制: " + param.getSourcePath() + " -> " + param.getTargetPath());
            } else {
                for (String file : param.getFiles()) {
                    log("开始文件复制: " + StringUtils.appendPath(param.getSourcePath(), file) + " -> " + StringUtils.appendPath(param.getTargetPath(), file));
                }
            }


            // 初始化复制过程回调
            this.initCallback();
            // 统计需要复制的总文件大小
            this.checkTotalSize();
            // 开始真正执行文件复制
            diskFileSystem.copy(param, progressCallback);

            // 任务完成，更新进度
            getProgress().setLoaded(getProgress().getTotal());
            CopyProgressRecord callbackProgressRecord = progressCallback.getProgressRecord();
            callbackProgressRecord.setCopiedFileSize(getProgress().getTotal());
            log("复制完成: " + StringUtils.getFormatSize(getProgress().getLoaded()));

        } catch (Exception e) {
            log.error("文件复制任务执行异常", e);
            log("复制异常: " + e.getMessage());
            if (e instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            running.set(false);
            executeThread.set(null);
            log("任务已退出");
            logWriter.close();
            logWriter = null;
        }
    }

    @Override
    public void interrupt() {
        if (!running.get()) {
            return;
        }
        interrupted.set(true);
        log("收到中断命令，正在停止...");
        if (progressCallback != null) {
            progressCallback.interrupt();
        }
        Thread thread = executeThread.get();
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getParams() {
        return originParams;
    }

    @Override
    public ProgressRecord getProgress() {
        return progressRecord;
    }
}