package com.saltedfishcloud.ext.vo.test;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.core.FFMpegHelperImpl;
import com.saltedfishcloud.ext.ve.model.*;
import com.saltedfishcloud.ext.ve.utils.StringParser;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.saltedfishcloud.ext.ve.constant.VEConstants.EncodeMethod;
import static com.saltedfishcloud.ext.ve.constant.VEConstants.EncoderType;

public class TestClass {
    private final String VIDEO_PATH = "C:\\Users\\xiaotao\\Downloads\\JoJo's Bizarre Adventure - S05E25 - DUAL 1080p WEB H.264 -NanDesuKa (NF).mkv";
    private final String VIDEO_PATH2 = "C:\\Users\\xiaotao\\Videos\\2022-09-01 12-59-04.mkv";
    private final String FFMPEG_PATH = "C:\\DATA\\soft\\ffmpeg-master-latest-win64-gpl\\ffmpeg-master-latest-win64-gpl\\bin";
    private final VEProperty PROPERTY = VEProperty.builder().ffmpegPath(FFMPEG_PATH).build();
    private final FFMpegHelper ffmpegHelper = new FFMpegHelperImpl(PROPERTY);

    /**
     * 测试解析mkv视频文件的章节锚点
     */
    @Test
    public void testParseChapter() throws IOException {
        VideoInfo videoInfo = ffmpegHelper.getVideoInfo(VIDEO_PATH);
        System.out.println(videoInfo);
    }

    @Test
    public void getInfo() throws Exception {
        FFMpegInfo ffMpegInfo = ffmpegHelper.getFFMpegInfo();
        System.out.println(ffMpegInfo);
    }

    @Test
    public void testConvert() throws Exception {
        VideoInfo videoInfo = ffmpegHelper.getVideoInfo(VIDEO_PATH2);
        List<EncodeConvertRule> ruleList = new ArrayList<>();
        // 把所有流添加到转换规则中
        // 视频流和字幕原样复制，音频流转acc
        for (StreamInfo stream : videoInfo.getStreams()) {
            EncodeConvertRule rule = new EncodeConvertRule();
            rule.setIndex(stream.getIndex());
            rule.setType(stream.getCodecType());
            if (EncoderType.VIDEO.equals(stream.getCodecType()) || EncoderType.SUBTITLE.equals(stream.getCodecType())) {
                rule.setMethod(EncodeMethod.COPY);
            } else if (EncoderType.AUDIO.equals(stream.getCodecType())){
                rule.setMethod(EncodeMethod.CONVERT).setEncoder("aac").setBitRate("320k");
            }
            ruleList.add(rule);
        }

        // ffmpeg执行
        Process process = ffmpegHelper.executeConvert(VIDEO_PATH2, "C:\\\\DATA\\\\output.mkv", EncodeConvertParam
                .builder()
                .rules(ruleList)
                .build());

        // 输出解析与进度计算
        try (InputStream in = process.getInputStream()) {
            Scanner scanner = new Scanner(in);
            scanner.useDelimiter("\r\n?");
            while (scanner.hasNext()) {
                String line = scanner.next();
                Double progress = StringParser.parseTimeProgress(line);
                if (progress != null) {
                    System.out.println(line);
                    System.out.println(StringUtils.getProcStr(progress.longValue(), videoInfo.getFormat().getDuration().longValue(), 64));
                }
            }
            process.waitFor();
            System.out.println("转换完成");
        }
    }
}
