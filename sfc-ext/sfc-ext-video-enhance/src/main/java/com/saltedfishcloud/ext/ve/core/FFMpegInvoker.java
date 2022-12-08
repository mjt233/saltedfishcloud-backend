package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.model.VEProperty;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FFMpegInvoker {

    private final VEProperty property;

    public FFMpegInvoker(VEProperty property) {
        this.property = property;
    }

    /**
     * 执行ffprobe命令
     * @param localFilePath 本地文件路径
     * @return              命令输出内容
     */
    public String executeProbe(String localFilePath) throws IOException {
        List<String> args = new ArrayList<>();
        args.add(property.getFFProbeExecPath());
        args.add("-i");
        args.add(localFilePath);
        Process process = this.executeCmd(args);
        try (InputStream is = process.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

    /**
     * 提取视频字幕
     * @param localFile 本地文件路径
     * @param streamNo  字幕流编号
     * @return          字幕文件srt内容
     */
    public String extractSubtitle(String localFile, String streamNo) throws IOException {
        Process process = executeCmd(property.getFFMpegExecPath(), "-i", localFile, "-map", streamNo, "-c", "copy", "-f", "srt", "-loglevel", "error", "-");
        try (InputStream is = process.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

    /**
     * 执行命令行
     */
    private Process executeCmd(List<String> args) throws IOException {

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(args);
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }

    /**
     * 执行命令行
     */
    private Process executeCmd(String... args) throws IOException {

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(args);
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }
}
