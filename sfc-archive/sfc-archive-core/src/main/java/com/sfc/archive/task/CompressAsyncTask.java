package com.sfc.archive.task;

import com.sfc.archive.ArchiveHandleEventListener;
import com.sfc.archive.ArchiveManager;
import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.sfc.archive.DiskFileSystemArchiveHelper;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.helper.CustomLogger;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

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
    private ArchiveManager archiveManager;

    private ArchiveCompressor compressor;

    private CustomLogger taskLog;

    private boolean inRunning = false;

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
     * 输出任务基础信息到日志
     * @param consumeTime   初始化耗时
     */
    private void logBasicInfo(long consumeTime) {

        taskLog.info(String.format("读取完成，耗时: %.2f s", consumeTime/1000.0));
        taskLog.info(String.format("总文件数: %d 文件大小: %s (%d)",
                compressor.getFileCount(),
                StringUtils.getFormatSize(compressor.getTotal()),
                compressor.getTotal())
        );
    }

    private void initEventHandler() {
        compressor.addEventListener(new ArchiveHandleEventListener() {
            @Override
            public void onDirCreate(ArchiveFile archiveFile) {
                taskLog.info("创建目录: " + archiveFile.getName());
            }

            @Override
            public void onFinish(long consumeTime) {
                taskLog.info("压缩任务完成，总耗时: " + consumeTime / 1000.0 + " s");
                log.debug("压缩任务完成，总耗时:{} s", consumeTime / 1000.0);
            }

            @Override
            public void onFileBeginHandle(ArchiveFile archiveFile) {
                log.debug("压缩文件:{}", archiveFile.getName());
                taskLog.info("开始压缩文件: " + archiveFile.getName() + "...");
            }

            @Override
            public void onFileFinishHandle(ArchiveFile archiveFile, long consumeTime) {
                progressRecord.setLoaded(compressor.getLoaded());
                taskLog.info("文件压缩完成: " + archiveFile.getName() + " - 耗时 " + consumeTime + " ms");
            }
        });
    }

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
        fileSystem.moveToSaveFile(compressParam.getSourceUid().intValue(), localPath, pb.range(-1), fileInfo);
    }

    @Override
    public void execute(OutputStream logOutputStream) {
        if (this.compressor != null || this.inRunning) {
            throw new IllegalArgumentException("重复执行");
        }
        this.inRunning = true;
        this.taskLog = new CustomLogger(logOutputStream);

        Path localPath = this.getTempFilePath();
        taskLog.info("任务参数: " + originParams);
        taskLog.info("创建本地临时文件: " + localPath);

        taskLog.info("正在读取待压缩文件列表...");
        long begin = System.currentTimeMillis();
        try {
            try (OutputStream outputStream = Files.newOutputStream(localPath);
                 ArchiveCompressor compressor = DiskFileSystemArchiveHelper.compressAndWriteOut(fileSystem, archiveManager, compressParam, outputStream)
            ) {

                this.compressor = compressor;

                // 输出初始化信息
                logBasicInfo(System.currentTimeMillis() - begin);
                progressRecord.setLoaded(compressor.getLoaded());
                progressRecord.setTotal(compressor.getTotal());

                // 绑定事件处理
                initEventHandler();

                // 开始压缩
                compressor.start();
            }
            // 保存压缩结果到网盘
            saveToFileSystem(localPath);

            taskLog.info("保存成功");
        }  catch (Throwable e) {
            log.error("任务异常",e);
            taskLog.error("任务异常：", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            taskLog.info("删除本地临时文件: " + localPath);
            try {
                Files.deleteIfExists(localPath);
            } catch (IOException e) {
                taskLog.error("本地文件删除失败： " + localPath, e);
            }
            taskLog.info("任务已退出");
            inRunning = false;
        }
    }

    @Override
    public void interrupt() {
        taskLog.warn("收到任务中断命令");
        if (compressor != null) {
            try {
                compressor.close();
            } catch (IOException e) {
                log.error("中断出错",e);
                if (taskLog != null) {
                    taskLog.error(e);
                }
            }
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
