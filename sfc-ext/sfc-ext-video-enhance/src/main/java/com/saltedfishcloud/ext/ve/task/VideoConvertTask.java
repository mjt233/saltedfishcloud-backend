package com.saltedfishcloud.ext.ve.task;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.ProcessWrap;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.saltedfishcloud.ext.ve.utils.StringParser;
import com.saltedfishcloud.ext.ve.utils.VideoResourceUtils;
import com.sfc.task.AsyncTask;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.helper.CustomLogger;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

/**
 * 异步任务实例模板 - 视频编码转换任务
 */
public class VideoConvertTask implements AsyncTask {
    /**
     * 日志输出
     */
    private CustomLogger logger;
    /**
     * 原始参数
     */
    private final String originParams;
    /**
     * 序列化后参数
     */
    private final EncodeConvertTaskParam param;

    /**
     * 任务进度
     */
    private final ProgressRecord progress = new ProgressRecord();


    /**
     * 本地文件系统中的视频文件路径
     */
    private String inputFile;

    /**
     * 资源服务，用于从网盘中读取资源和写入转换结果
     */
    private final ResourceService resourceService;

    /**
     * ffmpeg核心
     */
    private final FFMpegHelper ffMpegHelper;

    /**
     * 临时输出文件
     */
    private final String outputFile;

    private final AsyncTaskRecord asyncTaskRecord;
    /**
     * ffmpeg任务进程包装对象
     */
    private ProcessWrap processWrap = null;

    private boolean running;

    /**
     * 构造方法，根据{@link EncodeConvertTaskParam}对象的json进行反序列化作为参数
     */
    public VideoConvertTask(String jsonParam, ResourceService resourceService, FFMpegHelper ffMpegHelper, AsyncTaskRecord asyncTaskRecord) {
        try {
            this.asyncTaskRecord = asyncTaskRecord;
            this.originParams = jsonParam;
            this.resourceService = resourceService;
            this.ffMpegHelper = ffMpegHelper;
            this.param = MapperHolder.parseJson(jsonParam, EncodeConvertTaskParam.class);
            // 临时目录/ve/时间戳_文件名
            this.outputFile = StringUtils.appendSystemPath(
                    PathUtils.getTempDirectory(),
                    "ve",
                    System.currentTimeMillis() + "_" + param.getTarget().getName()
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("任务序列化失败:" + e.getMessage(), e);
        }

    }

    @Override
    public void execute(OutputStream logOutputStream) {
        if (isRunning()) {
            if (this.logger != null) {
                logger.warn("收到重复执行的命令");
            }
            throw new IllegalArgumentException("已经在运行中了");
        }
        running = true;
        logger = new CustomLogger(logOutputStream);

        Path nativeOutputFile = Paths.get(outputFile);
        try {
            initInputFile();
            FileUtils.createParentDirectory(outputFile);
            // 获取视频基础信息，记录总长
            VideoInfo videoInfo = ffMpegHelper.getVideoInfo(inputFile);

            // 进度总量+1是除了转码任务，还有转码完成后的存入网盘动作
            progress.setTotal(videoInfo.getFormat().getDuration().longValue() + 1);

            // 调用ffmpeg执行转换
            processWrap = ffMpegHelper.executeConvert(inputFile, outputFile, param);
            Optional.ofNullable(processWrap.getExtraMessage()).ifPresent(logger::info);

            // 用scanner按行读取解析进度
            Scanner scanner = new Scanner(processWrap.getProcess().getInputStream());
            scanner.useDelimiter("\r\n?");
            String line;
            try {
                boolean isRecordInProcessing = false;
                logger.info("command: " + Strings.join(processWrap.getArgs(), ' '));
                int lastProg = 0;
                while ((line = scanner.nextLine()) != null) {
                    Double progress = StringParser.parseTimeProgress(line);
                    if (progress != null) {
                        this.progress.setLoaded(progress.longValue());
                        if (!isRecordInProcessing) {
                            logger.info("已进入 ffmpeg 长耗时处理阶段，处理完成前不再记录ffmpeg原始输出日志");
                            logger.info(line);
                            isRecordInProcessing = true;
                        }
                        int curProg = (int) ((double) this.progress.getLoaded() / this.progress.getTotal() * 100);
                        if (curProg - lastProg >= 1) {
                            lastProg = curProg;
                            logger.info("进度更新: " + curProg + "%");
                        }
                    } else {
                        // 进度日志不记录
                        logger.info(line);
                    }
                }
            } catch (NoSuchElementException ignore) {
            }

            // 输出流中断，进程可能结束了
            logger.info("子进程控制台输出结束");
            // 确保真的结束
            int ret = processWrap.getProcess().waitFor();
            // 判断是不是异常退出
            if (ret != 0) {
                logger.info("子进程异常退出，代码: " + ret);
                throw new RuntimeException("ffmpeg异常退出: " + ret);
            }

            // 转换完成，保存输出和清理临时文件
            logger.info(inputFile + "转码完成，保存中");
            param.getTarget().getParams().put(ResourceRequest.CREATE_UID, asyncTaskRecord.getUid().toString());
            if (resourceService.getResource(param.getTarget()) != null) {
                if (Boolean.TRUE.equals(param.getIsOverwrite())) {
                    logger.warn("已存在同名文件，将覆盖原文件");
                } else {
                    String newName = asyncTaskRecord.getId() + "_" + param.getTarget().getName();
                    logger.warn("已存在同名文件，新文件自动重命名为: " + newName);
                    param.getTarget().setName(newName);
                }
            }
            if (ResourceProtocol.MAIN.equals(param.getTarget().getProtocol())) {
                logger.info("目标保存位置在网盘主文件系统，通过文件移动保存");
                long targetId = Long.parseLong(param.getTarget().getTargetId());
                UIDValidator.validateWithException(targetId, true);
                logger.info("开始计算文件md5");
                FileInfo fileInfo = FileInfo.getLocal(outputFile, true);
                fileInfo.setName(param.getTarget().getName());
                logger.info("开始保存");
                SpringContextUtils.getContext()
                        .getBean(DiskFileSystemManager.class)
                        .getMainFileSystem()
                        .moveToSaveFile(
                                targetId,
                                nativeOutputFile,
                                param.getTarget().getPath(),
                                fileInfo
                        );
            } else {
                logger.info("目标保存位置不在网盘主文件系统，使用通用资源写入功能保存");
                resourceService.writeResource(param.getTarget(), new PathResource(outputFile));
            }
            logger.info("保存完毕" + param.getTarget().getPath() + File.separator + param.getTarget().getName());
            this.progress.setLoaded(this.progress.getTotal());
        } catch (Exception e) {
            logger.error("编码转换失败:", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException("编码转换失败:" + e);
            }

        } finally {
            logger.info("删除临时输出文件: " + outputFile);
            try {
                Files.deleteIfExists(nativeOutputFile);
                logger.info("临时文件删除完成: " + outputFile);
            } catch (IOException e) {
                logger.error("删除临时文件出错", e);
            }
            if (processWrap != null) {
                processWrap.getProcess().destroy();
            }
            running = false;
        }
    }

    @Override
    public synchronized void interrupt() {
        if (logger != null) {
            logger.info("收到任务中断指令");
        }
        if (isRunning()) {
            processWrap.getProcess().destroy();
        } else {
            logger.info("任务没有在运行中，中断指令忽略");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getParams() {
        return originParams;
    }

    @Override
    public ProgressRecord getProgress() {
        return this.progress;
    }

    /**
     * 初始化输入文件信息
     */
    private void initInputFile() throws UnsupportedProtocolException, IOException {
        if (this.inputFile == null) {
            Resource resource = resourceService.getResource(param.getSource());
            Objects.requireNonNull(resource, "视频资源获取失败或已丢失");
            this.inputFile = VideoResourceUtils.toLocalPath(resource);
        }
    }

}
