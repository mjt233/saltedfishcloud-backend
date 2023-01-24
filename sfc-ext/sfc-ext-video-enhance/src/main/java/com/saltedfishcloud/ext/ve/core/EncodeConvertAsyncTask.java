package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.ProcessWrap;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.saltedfishcloud.ext.ve.utils.StringParser;
import com.xiaotao.saltedfishcloud.common.prog.ProgressProvider;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.service.async.io.impl.StringMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AbstractAsyncTask;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTaskResult;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Scanner;


@Accessors(chain = true)
@Slf4j
public class EncodeConvertAsyncTask extends AbstractAsyncTask<String, ProgressRecord> implements ProgressProvider {
    private final static String LOG_PREFIX = "[视频编码转换]";
    @Setter
    private EncodeConvertTaskParam param;

    /**
     * 转码任务记录id（不是异步任务id）
     */
    @Setter
    @Getter
    private Long id;

    @Setter
    private FFMpegHelper ffMpegHelper;

    @Setter
    private ResourceService resourceService;

    @Setter
    private String inputFile;

    @Setter
    private String outputFile;

    private final ProgressRecord status;

    private ProcessWrap processWrap = null;

    private boolean isFinish = false;

    /**
     * 日志暂存路径
     */
    @Getter
    private Path logPath;

    public EncodeConvertAsyncTask() {
        super(new StringMessageIOPair(), new StringMessageIOPair());
        status = new ProgressRecord();
    }

    private void initInputFile() throws UnsupportedProtocolException, IOException {
        if (this.inputFile == null) {
            Resource resource = resourceService.getResource(param.getSource());
            if (!(resource instanceof PathResource)) {
                throw new IllegalArgumentException("目前仅支持PathResource");
            }
            this.inputFile = ((PathResource) resource).getPath();
        }
    }

    /**
     * 获取输出的日志
     */
    public String getLog() {
        if (logPath == null) {
            return "";
        }
        try (InputStream inputStream = Files.newInputStream(logPath)) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("{}读取日志失败：", LOG_PREFIX, e);
            return "读取日志失败：" + e.getMessage();
        }
    }

    @Override
    protected AsyncTaskResult execute() {
        if (id == null) {
            throw new IllegalArgumentException("未设置id");
        }
        logPath = PathUtils.getLogDirectory().resolve("ffmpeg_" + id + ".log");
        try(OutputStream logOutput = Files.newOutputStream(logPath)) {
            log.info("{}创建ffmpeg日志文件: {}", LOG_PREFIX, logPath.toAbsolutePath());
            initInputFile();
            // 获取视频基础信息，记录总长
            VideoInfo videoInfo = ffMpegHelper.getVideoInfo(inputFile);
            status.setTotal(videoInfo.getFormat().getDuration().longValue());

            // 调用ffmpeg执行转换
            processWrap = ffMpegHelper.executeConvert(inputFile, outputFile, param);

            // 用scanner按行读取解析进度
            Scanner scanner = new Scanner(processWrap.getProcess().getInputStream());
            scanner.useDelimiter("\r\n?");
            String line;
            try {
                logOutput.write(("command: " + Strings.join(processWrap.getArgs(), ' ') + "\n\n").getBytes(StandardCharsets.UTF_8));
                while ((line = scanner.nextLine()) != null) {
                    logOutput.write(line.getBytes(StandardCharsets.UTF_8));
                    logOutput.write('\n');
                    Double progress = StringParser.parseTimeProgress(line);
                    if (progress != null) {
                        status.setLoaded(progress.longValue());
                        if(log.isDebugEnabled()) {
                            log.debug("{}{}", LOG_PREFIX, StringUtils.getProcStr(status.getLoaded(), status.getTotal(), 16));
                        }
                    }
                }
            } catch (NoSuchElementException ignore) { }
            logOutput.write("子进程控制台输出结束\n".getBytes(StandardCharsets.UTF_8));
            int ret = processWrap.getProcess().waitFor();
            if (ret != 0) {
                logOutput.write(("子进程异常退出，代码: " + ret +"\n").getBytes(StandardCharsets.UTF_8));
                throw new RuntimeException("ffmpeg异常退出：" + ret);
            }

            log.info("{}{}转码完成，保存中", LOG_PREFIX, inputFile);
            logOutput.write("转码完成，保存中\n".getBytes(StandardCharsets.UTF_8));
            resourceService.writeResource(param.getTarget(), new PathResource(outputFile));

            logOutput.write("保存完毕\n".getBytes(StandardCharsets.UTF_8));
            log.info("{}保存完毕：{}", LOG_PREFIX, param.getTarget().getPath() + File.separator + param.getTarget().getName());
            Files.deleteIfExists(Paths.get(outputFile));
            log.info("{}删除临时输出文件：{}", LOG_PREFIX, outputFile);
            return new AsyncTaskResult(AsyncTaskResult.Status.SUCCESS, 10);
        } catch (Exception e) {
            log.error("{}编码转换失败", LOG_PREFIX, e);
            return new AsyncTaskResult(AsyncTaskResult.Status.FAILED, 10);
        } finally {
            if (processWrap != null) {
                processWrap.getProcess().destroy();
            }
            isFinish = true;
        }
    }

    @Override
    public ProgressRecord getStatus() {
        return status;
    }

    @Override
    public ProgressRecord getProgressRecord() {
        return status;
    }

    @Override
    public boolean isStop() {
        return isFinish;
    }

    @Override
    protected void doInterrupt() {
        if (processWrap != null) {
            processWrap.getProcess().destroy();
        }
    }
}
