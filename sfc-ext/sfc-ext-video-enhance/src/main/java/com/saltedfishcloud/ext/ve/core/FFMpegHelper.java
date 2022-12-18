package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FFMpegHelper {
    /**
     * 媒体流编号匹配正则，取出实际编号，如：#0:45(chi) 匹配出0:45， #0:0 匹配出0:0
     */
    private final Pattern STREAM_NO_PATTERN = Pattern.compile("(?<=#)\\d+:\\d+(?=(\\[\\w+\\])?(\\(\\w+\\))?:)");


    @Setter
    @Getter
    private VEProperty property;

    public FFMpegHelper(VEProperty property) {
        this.property = property;
    }

    /**
     * 执行ffprobe命令
     * @param localFilePath 本地文件路径
     * @return              命令输出内容
     */
    public String executeProbe(String localFilePath) throws IOException {
        Process process = this.executeCmd(
                property.getFFProbeExecPath(),
                "-v", "quiet",
                "-print_format", "json",
                "-i", localFilePath,
                "-show_streams", "-show_chapters"
        );
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
        return extractSubtitle(localFile, streamNo, VEConstants.SUBTITLE_TYPE.SRT);
    }

    /**
     * 提取视频字幕
     * @param localFile 本地文件路径
     * @param streamNo  字幕流编号
     * @return          字幕文件srt内容
     */
    public String extractSubtitle(String localFile, String streamNo, String type) throws IOException {
        List<String> args = Stream.of(property.getFFMpegExecPath(), "-i", localFile, "-map", streamNo, "-f", type, "-loglevel", "error", "-").collect(Collectors.toList());
        Process process = executeCmd(args);
        try (InputStream is = process.getInputStream()) {
            String output = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            int i = process.waitFor();
            if (i != 0) {
                throw new RuntimeException("ffmpeg调用出错："+ String.join(" ", args) + "\n" + output);
            }
            return output;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
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

    /**
     * 获取视频信息
     * @param localFilePath 视频本地文件路径
     * @return              视频信息
     */
    public VideoInfo getVideoInfo(String localFilePath) throws IOException {
        String probeOutput = this.executeProbe(localFilePath);
        return MapperHolder.parseSnakeJson(probeOutput, VideoInfo.class);
    }
}
