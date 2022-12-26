package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.model.EncodeConvertProgress;
import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.saltedfishcloud.ext.ve.utils.StringParser;
import com.xiaotao.saltedfishcloud.common.prog.ProgressProvider;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.service.async.io.impl.StringMessageIOPair;
import com.xiaotao.saltedfishcloud.service.async.task.AbstractAsyncTask;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTaskResult;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;


@Accessors(chain = true)
@Slf4j
public class EncodeConvertAsyncTask extends AbstractAsyncTask<String, EncodeConvertProgress> implements ProgressProvider {
    private final static String LOG_PREFIX = "[视频编码转换]";
    @Setter
    private EncodeConvertTaskParam param;

    @Setter
    private FFMpegHelper ffMpegHelper;

    @Setter
    private String inputFile;

    @Setter
    private String outputFile;

    private final EncodeConvertProgress status;

    private boolean isFinish = false;

    public EncodeConvertAsyncTask() {
        super(new StringMessageIOPair(), new StringMessageIOPair());
        status = new EncodeConvertProgress();
    }

    @Override
    protected AsyncTaskResult execute() {
        Process process = null;
        try {
            // 获取视频基础信息，记录总长
            VideoInfo videoInfo = ffMpegHelper.getVideoInfo(inputFile);
            status.setTotal(videoInfo.getFormat().getDuration().longValue());

            // 调用ffmpeg执行转换
            process = ffMpegHelper.executeConvert(inputFile, outputFile, param);

            // 用scanner按行读取解析进度
            Scanner scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8);
            scanner.useDelimiter("\r\n?");
            String line;
            while ((line = scanner.nextLine()) != null) {
                outputProducer.write(line);
                Double progress = StringParser.parseTimeProgress(line);
                if (progress != null) {
                    status.setLoaded(progress.longValue());
                }
            }
            int ret = process.waitFor();
            if (ret != 0) {
                throw new RuntimeException("ffmpeg异常退出：" + ret);
            }
            return new AsyncTaskResult(AsyncTaskResult.Status.SUCCESS, 10);
        } catch (IOException | InterruptedException e) {
            log.error("{}编码转换失败", LOG_PREFIX, e);
            return new AsyncTaskResult(AsyncTaskResult.Status.FAILED, 10);
        } finally {
            if (process != null) {
                process.destroy();
            }
            isFinish = true;
        }
    }

    @Override
    public EncodeConvertProgress getStatus() {
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
}
