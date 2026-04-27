package com.sfc.archive.task;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineManager;
import com.sfc.archive.ArchiveEngineProvider;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.sfc.archive.utils.EngineResourceUtils;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.helper.CustomLogger;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 压缩任务
 */
@Slf4j
public class CompressAsyncTask implements AsyncTask {
    /**
     * 压缩参数
     */
    @Getter
    private final DiskFileSystemCompressParam compressParam;

    /**
     * 原始参数
     */
    @Getter
    private final String originParams;

    @Setter
    private DiskFileSystem fileSystem;

    @Setter
    private ArchiveEngineManager archiveEngineManager;

    private ArchiveEngineCompressor compressor;

    private CustomLogger taskLog;

    private boolean inRunning = false;

    /**
     * 是否已收到中断请求。
     */
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    private final AtomicReference<Thread> executeThread = new AtomicReference<>();

    /**
     * 在关键流程处检查中断请求，确保任务能够尽快停止。
     *
     * @throws InterruptedIOException 已收到中断请求时抛出
     */
    private void checkInterrupted() throws InterruptedIOException {
        if (interrupted.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("压缩任务已中断");
        }
    }

    public CompressAsyncTask(DiskFileSystemCompressParam compressParam, String originParams) {
        this.compressParam = compressParam;
        this.originParams = originParams;
    }

    private Path getTempFilePath() {
        Random random = new Random();
        int randomNum = random.nextInt(114514);
        return Paths.get(PathUtils.getTempDirectory() + "/temp_zip" + System.currentTimeMillis() + "_" + randomNum);
    }

    private final ProgressRecord progressRecord = new ProgressRecord();

    /**
     * 保存本地的压缩结果到网盘
     * @param localPath 本地压缩结果
     */
    private void saveToFileSystem(Path localPath) throws IOException {
        taskLog.info("压缩完成，正在保存到网盘..");
        final FileInfo fileInfo = FileInfo.getLocal(localPath.toString());
        PathBuilder pb = new PathBuilder();
        pb.setForcePrefix(true);
        pb.append(compressParam.getTargetFilePath());

        fileInfo.setName(pb.range(1, -1).replace("/", ""));
        fileSystem.moveToSaveFile(compressParam.getSourceUid(), localPath, pb.range(-1), fileInfo);
    }

    private FileInfo getTargetFileInfo() {
        FileInfo fileInfo = new FileInfo();
        long currentTimeMillis = System.currentTimeMillis();
        fileInfo.setCtime(currentTimeMillis);
        fileInfo.setMtime(currentTimeMillis);
        fileInfo.setName(PathUtils.getLastNode(compressParam.getTargetFilePath()));
        fileInfo.setPath(PathUtils.getParentPath(compressParam.getTargetFilePath()));
        fileInfo.setUid(compressParam.getTargetUid());
        return fileInfo;
    }


    /**
     * 将多个文件压缩，结果输出到outputStream
     * @param outputStream  压缩结果输出流
     */
    public void doCompressZipAndWriteOut(OutputStream outputStream) throws IOException {
        ArchiveEngineProvider engine = archiveEngineManager.getEngineProvider(compressParam.getEngineProviderId());

        Long uid = compressParam.getSourceUid();
        String path = compressParam.getSourcePath();
        Collection<String> names = compressParam.getSourceNames();
        try(ArchiveEngineCompressor compressor = engine.createCompressor(outputStream, compressParam.getEngineProperty())) {
            this.compressor = compressor;
            for (FileInfo fileInfo : fileSystem.getUserFileList(uid, path, names)) {
                checkInterrupted();
                if (fileInfo.isFile()) {
                    compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(fileInfo, "/", fileSystem.getResource(uid, path, fileInfo.getName())));
                } else {
                    addDirectoryResources(compressor, uid, path, fileInfo);
                }
            }
        } finally {
            this.compressor = null;
        }
    }

    /**
     * 遍历目录并在遍历完成后仅写入空目录项。
     * <p>
     * 目录在遍历过程中先全部记录到候选集合中；若某目录在遍历时发现存在直接子项，
     * 则加入非空目录集合。遍历结束后，仅将不在非空目录集合中的目录写入压缩包，
     * 从而保留空目录，同时避免为非空目录重复创建显式目录项。
     * </p>
     *
     * @param compressor     压缩器
     * @param uid            用户 ID
     * @param sourceBasePath 源目录路径
     * @param rootDirectory  起始目录
     * @throws IOException 遍历或写入失败
     */
    private void addDirectoryResources(ArchiveEngineCompressor compressor, Long uid, String sourceBasePath, FileInfo rootDirectory) throws IOException {
        checkInterrupted();
        String rootDirectoryPath = StringUtils.appendPath(sourceBasePath, rootDirectory.getName());
        Map<String, ArchiveResource> candidateDirectoryResources = new LinkedHashMap<>();
        candidateDirectoryResources.put(rootDirectoryPath, EngineResourceUtils.toArchiveResource(rootDirectory, "/", null));

        DiskFileSystemUtils.walk(fileSystem, uid, rootDirectoryPath, (curPath, subFiles) -> {
            checkInterrupted();
            if (!subFiles.isEmpty()) {
                candidateDirectoryResources.remove(curPath);
            }

            String basePath = StringUtils.removePrefix(sourceBasePath, curPath);
            if (basePath.isEmpty()) {
                basePath = "/";
            }

            for (FileInfo subFile : subFiles) {
                if (subFile.isFile()) {
                    compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(subFile, basePath, fileSystem.getResource(uid, curPath, subFile.getName())));
                } else {
                    String subDirectoryPath = StringUtils.appendPath(curPath, subFile.getName());
                    candidateDirectoryResources.put(subDirectoryPath, EngineResourceUtils.toArchiveResource(subFile, basePath, null));
                }
            }
        });

        for (Map.Entry<String, ArchiveResource> entry : candidateDirectoryResources.entrySet()) {
            checkInterrupted();
            compressor.addArchiveResource(entry.getValue());
        }
    }

    @Override
    public void execute(OutputStream logOutputStream) {
        if (this.compressor != null || this.inRunning) {
            throw new IllegalArgumentException("重复执行");
        }
        this.inRunning = true;
        interrupted.set(false);
        this.taskLog = new CustomLogger(logOutputStream);

        taskLog.info("任务参数: " + originParams);

        Objects.requireNonNull(compressParam.getEngineProviderId(), "engine provider id 不能为空");
        try {
            FileInfo targetFileInfo = getTargetFileInfo();
            fileSystem.saveFileByStream(targetFileInfo, PathUtils.getParentPath(compressParam.getTargetFilePath()), os -> {
                MeteredOutputStream meteredOutputStream = new MeteredOutputStream(os);
                executeThread.set(Thread.currentThread());
                doCompressZipAndWriteOut(meteredOutputStream);
                StreamCopyResult result = new StreamCopyResult(meteredOutputStream.getBytesWritten(), meteredOutputStream.getMd5());
                result.applyTo(targetFileInfo);
                return result;
            });
        } catch (Throwable e) {
            log.error("任务异常",e);
            taskLog.error("任务异常：", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            executeThread.set(null);
            taskLog.info("任务已退出");
            inRunning = false;
        }
    }

    @Override
    public void interrupt() {
        interrupted.set(true);
        if (taskLog != null) {
            taskLog.warn("收到任务中断命令");
        }
        ArchiveEngineCompressor compressor = this.compressor;
        if (compressor != null) {
            try {
                compressor.close();
            } catch (IOException e) {
                log.warn("中断时关闭压缩器失败", e);
            }
        }
        Thread thread = executeThread.get();
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isRunning() {
        return inRunning;
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
