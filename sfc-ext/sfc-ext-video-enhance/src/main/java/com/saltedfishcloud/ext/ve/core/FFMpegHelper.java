package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.model.Chapter;
import com.saltedfishcloud.ext.ve.model.MediaStream;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FFMpegHelper {
    /**
     * 媒体流编号匹配正则，取出实际编号，如：#0:45(chi) 匹配出0:45， #0:0 匹配出0:0
     */
    private final Pattern STREAM_NO_PATTERN = Pattern.compile("(?<=#)\\d+:\\d+(?=(\\(\\w+\\))?:)");


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
        return extractSubtitle(localFile, streamNo, VEConstants.SUBTITLE_TYPE.SRT);
    }

    /**
     * 提取视频字幕
     * @param localFile 本地文件路径
     * @param streamNo  字幕流编号
     * @return          字幕文件srt内容
     */
    public String extractSubtitle(String localFile, String streamNo, String type) throws IOException {
        Process process = executeCmd(property.getFFMpegExecPath(), "-i", localFile, "-map", streamNo, "-c", "copy", "-f", type, "-loglevel", "error", "-");
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

    /**
     * 获取视频信息
     * @param localFilePath 视频本地文件路径
     * @return              视频信息
     */
    public VideoInfo getVideoInfo(String localFilePath) throws IOException {
        VideoInfo videoInfo = new VideoInfo();
        String probeOutput = this.executeProbe(localFilePath);
        String[] outputArr = probeOutput.split("\r?\n");
        for (int i = 0; i < outputArr.length; i++) {
            String line = outputArr[i];
            if (!line.startsWith("  ")) {
                continue;
            }

            if (line.equals("  Chapters:")) {
                i = parseChapters(outputArr, i+1, videoInfo::addChapter);
            }
            if (line.startsWith("  Stream #")) {
                i = parseStream(outputArr, i, videoInfo::addStream);
            }
        }
        return videoInfo;
    }

    private Map<String, String> parseMetadata(String[] outputArr, int beginIndex, int indent) {
        String prefix = " ".repeat(indent);
        Map<String, String> metadata = new HashMap<>();
        for (int i = beginIndex; i < outputArr.length; i++) {
            String line = outputArr[i];
            if (!line.startsWith(prefix)) {
                return metadata;
            }
            String[] split = line.split(":", 2);
            if (split.length == 2) {
                metadata.put(split[0].trim(), split[1].trim());
            } else if (split.length == 1) {
                metadata.put(split[0].trim(), "");
            }
        }
        return metadata;
    }

    /**
     * 解析流数据
     * @param outputArr     原始输出字符串
     * @param beginIndex    开始查找的行数
     * @param consumer      流数据消费函数
     * @return              处理完成后的所处行数（处理最后一条数据时所处的行数）
     */
    private int parseStream(String[] outputArr, int beginIndex, Consumer<MediaStream> consumer) {
        for (int i = beginIndex; i < outputArr.length; i++) {
            String line = outputArr[i];
            if (!line.startsWith("  Stream")) {
                return i;
            }

            // 解析编号
            MediaStream stream = new MediaStream();
            Matcher matcher = STREAM_NO_PATTERN.matcher(line);
            if (matcher.find()) {
                stream.setNo(matcher.group());
                stream.setOriginLine(line);
            } else {
                continue;
            }


            if (i + 1 >= outputArr.length) {
                consumer.accept(stream);
                return i;
            }
            line = outputArr[++i];
            if (!line.startsWith("    Metadata:")) {
                consumer.accept(stream);
                continue;
            }
            Map<String, String> metadata = parseMetadata(outputArr, i + 1, 6);
            i += metadata.size();
            stream.setMetadata(metadata);
            consumer.accept(stream);
        }
        return outputArr.length - 1;
    }


    /**
     * 解析章节锚点
     * @param outputArr     原始输出字符串
     * @param beginIndex    开始查找的行数
     * @param consumer      章节锚点信息消费函数
     * @return              处理完成后的所处行数（处理最后一条数据时所处的行数）
     */
    private int parseChapters(String[] outputArr, int beginIndex, Consumer<Chapter> consumer) {

        for (int i = beginIndex; i < outputArr.length; i++) {
            String line = outputArr[i];
            if (!line.startsWith("    Chapter")) {
                return i - 1;
            }

            // 解析编号
            Chapter chapter = new Chapter();
            Matcher matcher = STREAM_NO_PATTERN.matcher(line);
            if (matcher.find()) {
                chapter.setNo(matcher.group());
            } else {
                continue;
            }


            // 解析跨度
            int startIdx = line.indexOf("start ");
            int splitIdx = line.indexOf(",");
            int endIdx = line.indexOf("end ");
            chapter.setStart((long) (Double.parseDouble(line.substring(startIdx + 6, splitIdx)) * 1000));
            chapter.setEnd((long) (Double.parseDouble(line.substring(endIdx + 4)) * 1000));

            if (i + 1 >= outputArr.length) {
                consumer.accept(chapter);
                return i - 1;
            }
            line = outputArr[++i];
            if (!line.equals("      Metadata:")) {
                consumer.accept(chapter);
                continue;
            }
            Map<String, String> metadata = parseMetadata(outputArr, i + 1, 8);
            i += metadata.size();
            chapter.setTitle(metadata.get("title"));
            consumer.accept(chapter);
        }
        return outputArr.length - 1;
    }
}
