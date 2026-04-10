package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.model.*;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.saltedfishcloud.ext.ve.constant.VEConstants.*;
import static com.saltedfishcloud.ext.ve.constant.VEConstants.EncoderType.*;

@Slf4j
public class FFMpegHelperImpl implements FFMpegHelper {

    @Setter
    @Getter
    private VEProperty property;

    public FFMpegHelperImpl(VEProperty property) {
        this.property = property;
    }

    /**
     * 执行ffprobe命令
     * @param localFilePath 本地文件路径
     * @return              命令输出内容
     */
    @Override
    public String executeProbe(String localFilePath) throws IOException {
        Process process = this.executeCmd(
                property.getFFProbeExecPath(),
                "-v", "quiet",
                "-print_format", "json",
                "-i", localFilePath,
                "-show_streams", "-show_chapters", "-show_format"
        ).getProcess();
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
    @Override
    public String extractSubtitle(String localFile, String streamNo) throws IOException {
        return extractSubtitle(localFile, streamNo, VEConstants.SubtitleType.SRT);
    }

    /**
     * 提取视频字幕
     * @param localFile 本地文件路径
     * @param streamNo  字幕流编号
     * @return          字幕文件srt内容
     */
    @Override
    public String extractSubtitle(String localFile, String streamNo, String type) throws IOException {
        String realStreamNo = streamNo.contains(":") ? streamNo : "0:" + streamNo;
        List<String> args = Stream.of(property.getFFMpegExecPath(), "-i", localFile, "-map", realStreamNo, "-f", type, "-loglevel", "error", "-").collect(Collectors.toList());
        Process process = executeCmd(args).getProcess();
        try (InputStream is = process.getInputStream()) {
            String output = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            int i = process.waitFor();
            if (i != 0) {
                throw new RuntimeException("FFmpeg调用出错："+ String.join(" ", args) + "\n" + output);
            }
            return output;
        } catch (InterruptedException e) {
            log.error("FFmpeg 提取字幕执行中断. file: {}, streamNo: {}, type: {}", localFile, streamNo, type, e);
            return null;
        }
    }

    /**
     * 执行命令行
     */
    private ProcessWrap executeCmd(List<String> args) throws IOException {
        return executeCmd(args, null);
    }

    private ProcessWrap executeCmd(List<String> argList, String[] argArr) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (argList != null) {
            processBuilder.command(argList);
        } else {
            processBuilder.command(argArr);
        }
        if (log.isDebugEnabled()) {
            log.debug("[FFMPEG]执行命令：" + Strings.join(processBuilder.command(), ' '));
        }
        processBuilder.redirectErrorStream(true);

        ProcessWrap processWrap = new ProcessWrap();
        processWrap.setProcess(processBuilder.start());
        processWrap.setArgs(processBuilder.command());
        return processWrap;
    }

    /**
     * 执行命令行
     */
    private ProcessWrap executeCmd(String... args) throws IOException {
        return executeCmd(null, args);
    }

    /**
     * 获取视频信息
     * @param localFilePath 视频本地文件路径
     * @return              视频信息
     */
    @Override
    public VideoInfo getVideoInfo(String localFilePath) throws IOException {
        String probeOutput = this.executeProbe(localFilePath);
        return MapperHolder.parseSnakeJson(probeOutput, VideoInfo.class);
    }

    /**
     * 获取ffmpeg的信息
     */
    @Override
    public FFMpegInfo getFFMpegInfo() throws IOException {
        Process process = this.executeCmd(property.getFFMpegExecPath(), "-encoders", "-v", "quiet").getProcess();
        String output = readOutput(process);
        String[] outputArr = output.split("\r?\n");

        FFMpegInfo ffMpegInfo = new FFMpegInfo();

        // 获取编码器支持
        Map<String, List<Encoder>> encodeTypeGroup = parseEncoderInfo(outputArr).stream().collect(Collectors.groupingBy(Encoder::getType));
        ffMpegInfo.setAudioEncoders(encodeTypeGroup.getOrDefault(VEConstants.EncoderType.AUDIO, Collections.emptyList()));
        ffMpegInfo.setVideoEncoders(encodeTypeGroup.getOrDefault(VIDEO, Collections.emptyList()));
        ffMpegInfo.setSubtitleEncoders(encodeTypeGroup.getOrDefault(VEConstants.EncoderType.SUBTITLE, Collections.emptyList()));
        ffMpegInfo.setOtherEncoders(encodeTypeGroup.getOrDefault(VEConstants.EncoderType.OTHER, Collections.emptyList()));

        String[] versionInfoArr = readOutput(this.executeCmd(property.getFFMpegExecPath(), "-version").getProcess()).split("\r?\n");
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
                encoder.setType(VIDEO);
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

    /**
     * 获取转码规则中原始输入文件的流索引对应的流信息
     */
    private StreamInfo getSourceStreamInfo(EncodeConvertRule rule, VideoInfo videoInfo) {
        try {
            String streamIndex = Optional.ofNullable(rule.getIndex()).filter(StringUtils::hasText).orElseThrow(() -> new IllegalArgumentException("rule stream index is empty or null"));
            return videoInfo.getStreams().get(Integer.parseInt(streamIndex));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("rule stream index format error: " + e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("rule stream index out of bounds, max index is: " + (videoInfo.getStreams().size() - 1));
        }
    }

    /**
     * 获取目标格式支持的流类型
     * @param format    目标格式（muxer或文件拓展名）
     * @return  {@link EncoderType}的Set集合
     */
    public Set<String> getFormatSupportStreamType(String format) {
        if ("mkv".equalsIgnoreCase(format) || "matroska".equalsIgnoreCase(format)) {
            return Set.of(
                    VIDEO, AUDIO ,SUBTITLE, DATA, ATTACHMENT
            );
        } else if (SubtitleType.ASS.equals(format) ||
                SubtitleType.SRT.equals(format) ||
                SubtitleType.WEBVTT.equals(format) ||
                SubtitleType.SSA.equals(format) ||
                SubtitleType.SUP.equals(format)) {
            return Set.of(SUBTITLE);
        } else {
            return Set.of(VIDEO, AUDIO);
        }
    }


    @Override
    public ProcessWrap executeFFMpeg(String input, String output, List<String> args) throws IOException {
        List<String> finalArgs = new ArrayList<>();
        finalArgs.add(property.getFFMpegExecPath());
        finalArgs.add("-hide_banner");
        finalArgs.add("-y");
        finalArgs.add("-i");
        finalArgs.add(input);
        finalArgs.addAll(args);
        finalArgs.add(output);
        return executeCmd(finalArgs);
    }

    @Override
    public ProcessWrap executeConvert(String input, String output, EncodeConvertParam param) throws IOException {
        List<EncodeConvertRule> rules = param.getRules();
        List<String> args = new ArrayList<>();

        VideoInfo inputVideoInfo = getVideoInfo(input);
        args.add(property.getFFMpegExecPath());
        args.add("-hide_banner");
        args.add("-y");
        args.add("-i");
        args.add(input);
        StringBuilder extraMessageBuilder = new StringBuilder();


        // 获取目标格式支持的流类型
        Set<String> formatSupportCodecType = getFormatSupportStreamType(
                Optional.ofNullable(param.getFormat())
                        .filter(StringUtils::hasText)
                        .orElseGet(() -> FileUtils.getSuffix(output))
        );

        // 记录各类型的流在输出的目标文件中的流索引
        // key - EncodeConvertRule#getType
        // ffmpeg中对于输入文件，流是不区分类型从0开始的，而对于输出文件，则是根据不同的流类型单独各自从0开始
        Map<String, AtomicInteger> outputStreamIndexMap = new HashMap<>();
        for (EncodeConvertRule rule : rules) {
            StreamInfo streamInfo = getSourceStreamInfo(rule, inputVideoInfo);
            // 判断目标格式是否支持对应的流类型，不支持的跳过。如：mp4不支持subtitle类型的流（字幕）
            if (!formatSupportCodecType.contains(streamInfo.getCodecType())) {
                extraMessageBuilder.append("target format is not support stream type, ignore process: ").append(streamInfo.getCodecType()).append("\n");
                continue;
            }

            // 指定要处理的输入流
            args.add("-map");
            args.add("0:" + rule.getIndex());

            // 指定流的编码处理方式
            int outputStreamIndex = outputStreamIndexMap
                    .computeIfAbsent(rule.getType(), k -> new AtomicInteger(-1))
                    .addAndGet(1);
            String outputStreamTag = rule.getTypeFlag() + ":" + outputStreamIndex;
            args.add("-c:" + outputStreamTag);
            if (EncodeMethod.COPY.equals(rule.getMethod())) {
                args.add("copy");
            } else if (EncodeMethod.CONVERT.equals(rule.getMethod())) {
                args.add(rule.getEncoder());
            } else {
                throw new IllegalArgumentException("未知的转换方式：" + rule.getMethod() + " 只支持copy或convert");
            }

            // 视频编码质量、速度与优化参数
            if (VIDEO.equals(rule.getType())) {
                Optional.ofNullable(rule.getCrf()).filter(StringUtils::hasText).ifPresent(crf -> {
                    args.add("-crf");
                    args.add(crf);
                });

                Optional.ofNullable(rule.getTune()).filter(StringUtils::hasText).ifPresent(tune -> {
                    args.add("-tune");
                    args.add(tune);
                });

                Optional.ofNullable(rule.getPreset()).filter(StringUtils::hasText).ifPresent(preset -> {
                    args.add("-preset");
                    args.add(preset);
                });
            }

            // 指定码率
            if (rule.getBitRate() != null) {
                args.add("-b:" + outputStreamTag);
                args.add(rule.getBitRate());
            }
        }

        // 封装格式指定
        if (param.getFormat() != null) {
            args.add("-f");
            args.add(param.getFormat());
        }
        args.add(output);
        ProcessWrap wrap = executeCmd(args);
        wrap.setExtraMessage(extraMessageBuilder.toString());
        return wrap;
    }
}
