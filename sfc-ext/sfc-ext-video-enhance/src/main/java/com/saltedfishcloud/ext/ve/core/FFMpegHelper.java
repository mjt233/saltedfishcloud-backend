package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.model.Encoder;
import com.saltedfishcloud.ext.ve.model.FFMpegInfo;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FFMpegHelper {

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
                "-show_streams", "-show_chapters", "-show_format"
        );
        try (InputStream is = process.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

    protected String readOutput(Process process) throws IOException {
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
        return extractSubtitle(localFile, streamNo, VEConstants.SubtitleType.SRT);
    }

    /**
     * 提取视频字幕
     * @param localFile 本地文件路径
     * @param streamNo  字幕流编号
     * @return          字幕文件srt内容
     */
    public String extractSubtitle(String localFile, String streamNo, String type) throws IOException {
        String realStreamNo = streamNo.contains(":") ? streamNo : "0:" + streamNo;
        List<String> args = Stream.of(property.getFFMpegExecPath(), "-i", localFile, "-map", realStreamNo, "-f", type, "-loglevel", "error", "-").collect(Collectors.toList());
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

    /**
     * 获取ffmpeg的信息
     */
    public FFMpegInfo getFFMpegInfo() throws IOException {
        Process process = this.executeCmd(property.getFFMpegExecPath(), "-encoders", "-v", "quiet");
        String output = readOutput(process);
        String[] outputArr = output.split("\r?\n");

        FFMpegInfo ffMpegInfo = new FFMpegInfo();

        // 获取编码器支持
        Map<String, List<Encoder>> encodeTypeGroup = parseEncoderInfo(outputArr).stream().collect(Collectors.groupingBy(Encoder::getType));
        ffMpegInfo.setAudioEncoders(encodeTypeGroup.getOrDefault(VEConstants.EncoderType.AUDIO, Collections.emptyList()));
        ffMpegInfo.setVideoEncoders(encodeTypeGroup.getOrDefault(VEConstants.EncoderType.VIDEO, Collections.emptyList()));
        ffMpegInfo.setSubtitleEncoders(encodeTypeGroup.getOrDefault(VEConstants.EncoderType.SUBTITLE, Collections.emptyList()));
        ffMpegInfo.setOtherEncoders(encodeTypeGroup.getOrDefault(VEConstants.EncoderType.OTHER, Collections.emptyList()));

        String[] versionInfoArr = readOutput(this.executeCmd(property.getFFMpegExecPath(), "-version")).split("\r?\n");
        ffMpegInfo.setVersion(versionInfoArr[0].split("\\s+")[2]);
        ffMpegInfo.setBuilt(versionInfoArr[1].split("\\s+", 3)[2]);
        ffMpegInfo.setConfiguration(versionInfoArr[2].split(": ")[1]);

        return ffMpegInfo;
    }

    private List<Encoder> parseEncoderInfo(String[] output) {
        List<Encoder> res = new ArrayList<>();
        boolean find = false;
        for (String line : output) {
            if (!find) {
                if (line.contains("----")) {
                    find = true;
                }
                continue;
            }

            Encoder encoder = new Encoder();
            String[] split = line.trim().split("\\s+", 3);
            char type = split[0].charAt(0);
            if (type == 'V') {
                encoder.setType(VEConstants.EncoderType.VIDEO);
            } else if (type == 'A') {
                encoder.setType(VEConstants.EncoderType.AUDIO);
            } else if (type == 'S') {
                encoder.setType(VEConstants.EncoderType.SUBTITLE);
            } else {
                encoder.setType(VEConstants.EncoderType.OTHER);
            }
            encoder.setFlag(split[0]);
            encoder.setName(split[1]);
            encoder.setDescribe(split[2]);
            res.add(encoder);
        }
        return res;
    }
}
