package com.sfc.archive.task;

import com.sfc.archive.ArchiveHandleEventListener;
import com.sfc.archive.ArchiveManager;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.AsyncArchiveExtractParam;

import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.helper.CustomLogger;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressRecord;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文件在线解压异步任务。
 * <p>
 * 通过 {@link ResourceService} 获取待解压的文件资源，使用 {@link ArchiveManager} 创建对应格式的
 * 解压器，将文件解压到本地临时目录后再通过 {@link DiskFileSystem} 保存到目标网盘目录。
 * 解压过程支持 {@link FileTransferCallback} 回调，实时将进度信息输出到任务日志。
 * </p>
 */
@Slf4j
public class ArchiveExtractAsyncTask implements AsyncTask {

    /** 保存到网盘时，每批处理的最大文件数量，避免待保存列表过大引发内存压力 */
    private static final int SAVE_BATCH_SIZE = 500;

    /** 序列化的原始任务参数字符串 */
    private final String originParams;

    /** 解压任务参数 */
    private final AsyncArchiveExtractParam param;

    @Setter
    private ResourceService resourceService;

    @Setter
    private ArchiveManager archiveManager;

    @Setter
    private DiskFileSystem diskFileSystem;

    /** 任务日志输出器 */
    private CustomLogger taskLog;

    /** 文件传输进度回调，用于实时记录解压/保存进度 */
    private FileTransferCallback fileTransferCallback;

    /** 当前是否正在运行 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 是否已被中断 */
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    /** 执行任务的线程引用，用于中断支持 */
    private final AtomicReference<Thread> executeThread = new AtomicReference<>();

    /** 解压进度记录 */
    private final ProgressRecord progressRecord = new ProgressRecord();

    /**
     * 构造解压异步任务
     *
     * @param originParams 序列化的原始参数字符串
     * @param param        解析后的解压任务参数
     */
    public ArchiveExtractAsyncTask(String originParams, AsyncArchiveExtractParam param) {
        this.originParams = originParams;
        this.param = param;
    }

    /**
     * 构建 FileTransferCallback，将解压过程中的文件事件以日志形式输出到任务日志流。
     *
     * @return 与任务日志绑定的 FileTransferCallback 实例
     */
    private FileTransferCallback buildFileTransferCallback() {
        return new FileTransferCallback() {
            private final CopyProgressRecord record = new CopyProgressRecord();

            @Override
            public CopyProgressRecord getProgressRecord() {
                return record;
            }

            @Override
            public void onFileStart(FileTransferItem item) {
                taskLog.info("[解压] 提取文件: " + item.getTo());
            }

            @Override
            public void onFileComplete(FileTransferItem item) {
                record.setCopiedFileCount(record.getCopiedFileCount() + 1);
                if (item.getTotal() != null) {
                    record.setCopiedFileSize(record.getCopiedFileSize() + item.getTotal());
                }
            }

            @Override
            public void onDirStart(String dirPath) {
                taskLog.info("[解压] 创建目录: " + dirPath);
            }

            @Override
            public void onDirComplete(String dirPath) {
                record.setCreatedDirCount(record.getCreatedDirCount() + 1);
            }

            @Override
            public boolean shouldInterrupt() {
                return interrupted.get() || record.isInterrupted();
            }
        };
    }

    /**
     * 构建 ArchiveHandleEventListener，将解压器内部事件桥接到 {@link FileTransferCallback}
     * 并更新任务进度记录。
     *
     * @param callback 目标 FileTransferCallback
     * @return 绑定了回调的事件监听器
     */
    private ArchiveHandleEventListener buildEventListener(FileTransferCallback callback) {
        return new ArchiveHandleEventListener() {

            @Override
            public void onFileBeginHandle(ArchiveFile archiveFile) {
                String targetPath = StringUtils.appendPath(param.getPath(), archiveFile.getPath());
                FileTransferItem item = FileTransferItem.builder()
                        .from(archiveFile.getPath())
                        .to(targetPath)
                        .total(archiveFile.getSize())
                        .loaded(0L)
                        .build();
                callback.onFileStart(item);
            }

            @Override
            public void onFileFinishHandle(ArchiveFile archiveFile, long consumeTime) {
                String targetPath = StringUtils.appendPath(param.getPath(), archiveFile.getPath());
                FileTransferItem item = FileTransferItem.builder()
                        .from(archiveFile.getPath())
                        .to(targetPath)
                        .total(archiveFile.getSize())
                        .loaded(archiveFile.getSize())
                        .build();
                progressRecord.setLoaded(progressRecord.getLoaded() + archiveFile.getSize());
                callback.onFileComplete(item);
            }

            @Override
            public void onDirCreate(ArchiveFile archiveFile) {
                String dirPath = StringUtils.appendPath(param.getPath(), archiveFile.getPath());
                callback.onDirStart(dirPath);
                callback.onDirComplete(dirPath);
            }

            @Override
            public void onFinish(long consumeTime) {
                taskLog.info(String.format("解压完成，总耗时: %.2f s", consumeTime / 1000.0));
            }

            @Override
            public void onError(ArchiveFile archiveFile, Throwable throwable) {
                String name = archiveFile != null ? archiveFile.getPath() : "未知文件";
                taskLog.error("解压出错，文件: " + name, throwable);
            }
        };
    }

    /**
     * 立即处理并清空待保存文件列表，避免内存中堆积过多待保存项。
     *
     * @param pendingSaveFiles 待保存文件列表
     * @param saveCallback     保存回调
     * @throws IOException 任务中断时抛出
     */
    private void flushPendingSaveFiles(List<FileInfo> pendingSaveFiles, FileTransferCallback saveCallback) throws IOException {
        if (pendingSaveFiles.isEmpty()) {
            return;
        }
        if (interrupted.get() || saveCallback.shouldInterrupt()) {
            throw new InterruptedIOException("任务已中断，停止保存文件");
        }
        diskFileSystem.batchSaveFiles(new ArrayList<>(pendingSaveFiles), saveCallback);
        pendingSaveFiles.clear();
    }

    /**
     * 将本地临时解压目录中的所有文件保存到目标网盘目录，并通过 FileTransferCallback 输出实时日志。
     *
     * @param tempBasePath 本地临时解压目录
     * @throws IOException 文件操作异常
     */
    private void saveToDisk(Path tempBasePath) throws IOException {
        final int tempLen = tempBasePath.toString().length();
        final long uid = param.getUid();
        final String dest = param.getPath();
        final List<FileInfo> pendingSaveFiles = new ArrayList<>();
        final FileTransferCallback saveCallback = new FileTransferCallback() {
            @Override
            public CopyProgressRecord getProgressRecord() {
                return fileTransferCallback.getProgressRecord();
            }

            @Override
            public void onFileStart(FileTransferItem item) {
                fileTransferCallback.onFileStart(item);
            }

            @Override
            public void onFileComplete(FileTransferItem item) {
                fileTransferCallback.onFileComplete(item);
                if (item.getTotal() != null && item.getTotal() > 0) {
                    progressRecord.setLoaded(progressRecord.getLoaded() + item.getTotal());
                }
            }

            @Override
            public void onAdditionalEvent(com.xiaotao.saltedfishcloud.model.progress.CopyProgressEvent event) {
                fileTransferCallback.onAdditionalEvent(event);
            }

            @Override
            public void onDirStart(String dirPath) {
                fileTransferCallback.onDirStart(dirPath);
            }

            @Override
            public void onDirComplete(String dirPath) {
                fileTransferCallback.onDirComplete(dirPath);
            }

            @Override
            public boolean shouldInterrupt() {
                return fileTransferCallback.shouldInterrupt();
            }
        };

        Files.walkFileTree(tempBasePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (interrupted.get()) {
                    return FileVisitResult.TERMINATE;
                }
                String diskPath = StringUtils.appendPath(dest,
                        dir.toString().substring(tempLen).replaceAll("\\\\+", "/"));
                fileTransferCallback.onDirStart(diskPath);
                if (!diskFileSystem.exist(uid, diskPath)) {
                    diskFileSystem.mkdirs(uid, diskPath);
                }
                fileTransferCallback.onDirComplete(diskPath);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (interrupted.get()) {
                    return FileVisitResult.TERMINATE;
                }
                String diskDir = StringUtils.appendPath(dest,
                        file.getParent().toString().substring(tempLen).replaceAll("\\\\+", "/"));
                FileInfo fileInfo = FileInfo.getLocal(file.toString(), false);
                fileInfo.setUid(uid);
                fileInfo.setPath(diskDir);
                pendingSaveFiles.add(fileInfo);

                if (pendingSaveFiles.size() >= SAVE_BATCH_SIZE) {
                    flushPendingSaveFiles(pendingSaveFiles, saveCallback);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                taskLog.error("访问文件失败: " + file, exc);
                return FileVisitResult.CONTINUE;
            }
        });

        flushPendingSaveFiles(pendingSaveFiles, saveCallback);
    }

    @Override
    public void execute(OutputStream logOutputStream) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("任务正在执行中，不能重复执行");
        }
        executeThread.set(Thread.currentThread());
        taskLog = new CustomLogger(logOutputStream);
        fileTransferCallback = buildFileTransferCallback();

        // 创建本地临时目录
        Path tempBasePath = Paths.get(StringUtils.appendPath(
                PathUtils.getTempDirectory(),
                "extract_" + System.currentTimeMillis()
        ));

        taskLog.info("任务参数: " + originParams);
        taskLog.info("解压目标目录: " + param.getPath() + " (uid=" + param.getUid() + ")");

        try {
            // 1. 获取源文件资源
            taskLog.info("正在获取源文件资源...");
            Resource resource = resourceService.getResource(param.getSource());
            if (resource == null) {
                throw new IllegalArgumentException("无法获取源文件资源: " + param.getSource().getName());
            }
            taskLog.info("源文件资源获取成功: " + resource.getFilename());

            // 2. 获取解压器
            try (ArchiveExtractor extractor = archiveManager.getExtractor(param.getArchiveParam(), resource)) {
                // 3. 注册事件监听器，桥接 FileTransferCallback
                extractor.addEventListener(buildEventListener(fileTransferCallback));

                // 监听临时文件拉取事件（非本地存储时会先下载到本地）
                extractor.onResourceBeginFetch(tempPath ->
                        taskLog.info("源文件不在本地，正在下载到临时文件: " + tempPath));
                extractor.onResourceFinishFetch(tempPath ->
                        taskLog.info("临时文件下载完成: " + tempPath));

                // 4. 解压到临时目录
                Files.createDirectories(tempBasePath);
                taskLog.info("创建临时目录: " + tempBasePath);
                taskLog.info("开始解压文件...");

                // 统计总共的解压后文件大小
                for (ArchiveFile archiveFile : extractor.listFiles()) {
                    if(!archiveFile.isDirectory()) {
                        if (archiveFile.getSize() >= 0) {
                            if (progressRecord.getTotal() <= 0) {
                                progressRecord.setTotal(archiveFile.getSize());
                            } else {
                                progressRecord.setTotal(progressRecord.getTotal() + archiveFile.getSize());
                            }
                        } else {
                            // 大小位置，跳过处理
                            progressRecord.setTotal(-1L);
                            break;
                        }
                    }
                }
                // 先将文件提取到本地文件系统的临时目录
                extractor.extractAll(tempBasePath);
                // 重置进度为0，后续将文件从本地文件系统转移到用户网盘
                progressRecord.setLoaded(0L);

                // 5. 将临时目录中的文件保存到目标网盘目录
                if (interrupted.get()) {
                    taskLog.warn("任务已被中断，跳过保存步骤");
                    return;
                }
                taskLog.info("解压完成，正在将文件保存到网盘目录: " + param.getPath());
                saveToDisk(tempBasePath);
                taskLog.info("所有文件已成功保存到网盘");
            }
        } catch (Throwable e) {
            log.error("解压任务异常", e);
            taskLog.error("解压任务异常: ", e);
            if (e instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            // 清理临时目录
            if (Files.exists(tempBasePath)) {
                taskLog.info("清理临时目录: " + tempBasePath);
                try {
                    FileUtils.delete(tempBasePath);
                } catch (IOException e) {
                    taskLog.error("临时目录清理失败: " + tempBasePath, e);
                }
            }
            taskLog.info("任务已退出");
            running.set(false);
            executeThread.set(null);
        }
    }

    @Override
    public void interrupt() {
        if (!running.get()) {
            return;
        }
        interrupted.set(true);
        taskLog.warn("收到任务中断命令，正在停止解压...");
        if (fileTransferCallback != null) {
            fileTransferCallback.interrupt();
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


