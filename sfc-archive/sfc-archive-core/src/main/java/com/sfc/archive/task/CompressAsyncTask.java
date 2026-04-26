package com.sfc.archive.task;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineManager;
import com.sfc.archive.ArchiveEngineProvider;
import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.sfc.archive.utils.EngineResourceUtils;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.helper.CustomLogger;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.utils.DiskFileSystemUtils;
import com.xiaotao.saltedfishcloud.utils.MeteredOutputStream;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StreamCopyResult;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicReference<Thread> executeThread = new AtomicReference<>();

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

    @Override
    public void execute(OutputStream logOutputStream) {
        if (this.compressor != null || this.inRunning) {
            throw new IllegalArgumentException("重复执行");
        }
        this.inRunning = true;
        this.taskLog = new CustomLogger(logOutputStream);

        taskLog.info("任务参数: " + originParams);

        String engineProviderId = Objects.requireNonNull(compressParam.getEngineProviderId(), "engine provider id 不能为空");
        ArchiveEngineProvider engineProvider = archiveEngineManager.getEngineProvider(engineProviderId);

        try {
            FileInfo targetFileInfo = getTargetFileInfo();
            fileSystem.saveFileByStream(targetFileInfo, PathUtils.getParentPath(compressParam.getTargetFilePath()), os -> {
                MeteredOutputStream meteredOutputStream = new MeteredOutputStream(os);
                try (ArchiveEngineCompressor compressor = engineProvider.createCompressor(meteredOutputStream, compressParam.getEngineProperty())) {
                    this.compressor = compressor;
                    executeThread.set(Thread.currentThread());
                    for (FileInfo fileInfo : fileSystem.getUserFileList(compressParam.getSourceUid(), compressParam.getSourcePath(), compressParam.getSourceNames())) {
                        if (fileInfo.isFile()) {
                            compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(fileInfo, "/", fileSystem.getResource(compressParam.getSourceUid(), compressParam.getSourcePath(), fileInfo.getName())));
                        } else {
                            compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(fileInfo, "/", null));
                            DiskFileSystemUtils.walk(fileSystem, compressParam.getSourceUid(), StringUtils.appendPath(compressParam.getSourcePath(), fileInfo.getName()), (curPath, subFiles) -> {
                                String basePath = StringUtils.removePrefix(compressParam.getSourcePath(), curPath);
                                if (basePath.isEmpty()) {
                                    basePath = "/";
                                }
                                for (FileInfo subFile : subFiles) {
                                    if (subFile.isFile()) {
                                        compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(subFile, basePath, fileSystem.getResource(compressParam.getSourceUid(), curPath, subFile.getName())));
                                    } else {
                                        compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(subFile, basePath, null));
                                    }
                                }
                            });
                        }
                    }
                    StreamCopyResult result = new StreamCopyResult(meteredOutputStream.getBytesWritten(), meteredOutputStream.getMd5());
                    result.applyTo(targetFileInfo);
                    return result;
                }
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
        taskLog.warn("收到任务中断命令");
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
