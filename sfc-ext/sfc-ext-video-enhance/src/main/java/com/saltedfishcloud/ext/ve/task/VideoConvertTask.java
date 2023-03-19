package com.saltedfishcloud.ext.ve.task;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.ProcessWrap;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.saltedfishcloud.ext.ve.utils.StringParser;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.helper.CustomLogger;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
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

    /**
     * ffmpeg任务进程包装对象
     */
    private ProcessWrap processWrap = null;

    private boolean running;

    /**
     * 构造方法，根据{@link EncodeConvertTaskParam}对象的json进行反序列化作为参数
     */
    public VideoConvertTask(String jsonParam, ResourceService resourceService, FFMpegHelper ffMpegHelper) {
        try {
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

        try {
            initInputFile();
            FileUtils.createParentDirectory(outputFile);
            // 获取视频基础信息，记录总长
            VideoInfo videoInfo = ffMpegHelper.getVideoInfo(inputFile);
            progress.setTotal(videoInfo.getFormat().getDuration().longValue());

            // 调用ffmpeg执行转换
            processWrap = ffMpegHelper.executeConvert(inputFile, outputFile, param);

            // 用scanner按行读取解析进度
            Scanner scanner = new Scanner(processWrap.getProcess().getInputStream());
            scanner.useDelimiter("\r\n?");
            String line;
            try {
                logger.info("command: " + Strings.join(processWrap.getArgs(), ' '));
                while ((line = scanner.nextLine()) != null) {
                    logger.info(line);
                    Double progress = StringParser.parseTimeProgress(line);
                    if (progress != null) {
                        this.progress.setLoaded(progress.longValue());
                    }
                }
            } catch (NoSuchElementException ignore) { }

            // 输出流中断，进程可能结束了
            logger.info("子进程控制台输出结束");
            // 确保真的结束
            int ret = processWrap.getProcess().waitFor();
            // 判断是不是异常退出
            if (ret != 0) {
                logger.info("子进程异常退出，代码: "+ ret);
                throw new RuntimeException("ffmpeg异常退出: " + ret);
            }

            // 转换完成，保存输出和清理临时文件
            logger.info(inputFile + "转码完成，保存中");
            resourceService.writeResource(param.getTarget(), new PathResource(outputFile));
            logger.info("保存完毕" + param.getTarget().getPath() + File.separator + param.getTarget().getName());
            logger.info("删除临时输出文件: " + outputFile);
            Files.deleteIfExists(Paths.get(outputFile));
            logger.info("临时文件删除完成: " + outputFile);
        } catch (Exception e) {
            logger.error("编码转换失败:", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else {
                throw new RuntimeException("编码转换失败:" + e);
            }

        } finally {
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
            if (!(resource instanceof PathResource)) {
                throw new IllegalArgumentException("目前仅支持PathResource");
            }
            this.inputFile = ((PathResource) resource).getPath();
        }
    }

}
