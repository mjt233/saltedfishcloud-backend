package com.sfc.archive.task;

import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.ArchiveEngineManager;
import com.sfc.archive.model.ArchiveResource;
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
import java.io.InputStream;
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
 * 通过 {@link ResourceService} 获取待解压的文件资源，使用 {@link ArchiveEngineManager} 创建解压器，
 * 将文件解压到本地临时目录后再通过 {@link DiskFileSystem} 保存到目标网盘目录。
 * 解压过程会输出“当前文件”日志并更新总进度。
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
    private ArchiveEngineManager archiveEngineManager;

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
     * 构建引擎解压回调：输出当前文件日志并维护总进度。
     *
     * @return 引擎文件传输回调
     */
    private com.sfc.archive.model.FileTransferCallback buildEngineTransferCallback() {
        return new com.sfc.archive.model.FileTransferCallback() {
            private long lastLoaded;

            @Override
            public void onFileStart(String archivePath) {
                lastLoaded = 0L;
                taskLog.info("[解压] 当前文件: " + archivePath);
            }

            @Override
            public void onProgress(String archivePath, long loaded, long total) {
                if (loaded <= lastLoaded) {
                    return;
                }
                long delta = loaded - lastLoaded;
                long currentLoaded = progressRecord.getLoaded();
                progressRecord.setLoaded(Math.max(0L, currentLoaded) + delta);
                lastLoaded = loaded;
            }
        };
    }

    /**
     * 预统计压缩包中文件总字节数，用于初始化总进度。
     *
     * @param decompressor 解压器
     * @throws IOException 读取资源列表失败
     */
    private void initTotalProgress(ArchiveEngineDecompressor decompressor) throws IOException {
        long total = 0L;
        for (var iterator = decompressor.getArchiveResources(); iterator.hasNext(); ) {
            ArchiveResource archiveResource = iterator.next();
            if (Boolean.TRUE.equals(archiveResource.getIsDirectory())) {
                continue;
            }
            Long size = archiveResource.getSize();
            if (size == null || size < 0) {
                progressRecord.setTotal(-1L);
                return;
            }
            total += size;
        }
        progressRecord.setTotal(total);
    }

    /**
     * 将压缩包内资源路径映射到临时目录，并校验不允许越界路径。
     *
     * @param tempBasePath 临时目录基路径
     * @param archivePath  压缩包内路径
     * @return 对应的本地路径
     */
    private Path resolveArchiveResourcePath(Path tempBasePath, String archivePath) {
        Path targetPath = tempBasePath.resolve(archivePath).normalize();
        if (!targetPath.startsWith(tempBasePath)) {
            throw new IllegalArgumentException("非法压缩包路径: " + archivePath);
        }
        return targetPath;
    }

    /**
     * 将压缩包内容解压到本地临时目录。
     *
     * @param decompressor 解压器
     * @param tempBasePath 临时目录基路径
     * @throws IOException 解压失败
     */
    private void decompressToTempDirectory(ArchiveEngineDecompressor decompressor, Path tempBasePath) throws IOException {
        decompressor.decompressAll((inputStream, archiveResource) -> {
            if (interrupted.get()) {
                return false;
            }

            Path targetPath = resolveArchiveResourcePath(tempBasePath, archiveResource.getArchivePath());
            if (Boolean.TRUE.equals(archiveResource.getIsDirectory())) {
                if (!Files.exists(targetPath)) {
                    Files.createDirectories(targetPath);
                    return true;
                }
                if (!Files.isDirectory(targetPath)) {
                    throw new IOException("路径冲突，无法创建目录: " + targetPath);
                }
            }

            Path parentPath = targetPath.getParent();
            if (parentPath != null) {
                Files.createDirectories(parentPath);
            }

            if (inputStream == null) {
                throw new IOException("文件资源输入流为空: " + archiveResource.getArchivePath());
            }
            try (InputStream resourceInputStream = inputStream) {
                Files.copy(resourceInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        });
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
            try (ArchiveEngineDecompressor decompressor = archiveEngineManager.createEngineDecompressor(
                    param.getEngineProviderId(), resource, param.getArchiveEngineProperty())) {
                // 3. 解压到临时目录
                Files.createDirectories(tempBasePath);
                taskLog.info("创建临时目录: " + tempBasePath);
                taskLog.info("开始解压文件...");

                // 初始化总进度并绑定过程回调
                progressRecord.setLoaded(0L);
                initTotalProgress(decompressor);
                decompressor.setCallback(buildEngineTransferCallback());

                // 先将文件提取到本地文件系统的临时目录
                decompressToTempDirectory(decompressor, tempBasePath);

                // 4. 将临时目录中的文件保存到目标网盘目录
                if (interrupted.get()) {
                    taskLog.warn("任务已被中断，跳过保存步骤");
                    return;
                }
                taskLog.info("解压完成，正在将文件保存到网盘目录: " + param.getPath());
                // 重置进度，进入保存阶段重新计量
                progressRecord.setLoaded(0L);
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


