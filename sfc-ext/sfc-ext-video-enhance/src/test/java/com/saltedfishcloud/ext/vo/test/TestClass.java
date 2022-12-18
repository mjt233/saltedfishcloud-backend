package com.saltedfishcloud.ext.vo.test;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestClass {
    private final String VIDEO_PATH = "C:\\Users\\xiaotao\\Downloads\\JoJo's Bizarre Adventure - S05E25 - DUAL 1080p WEB H.264 -NanDesuKa (NF).mkv";
    private final String FFMPEG_PATH = "C:\\DATA\\soft\\ffmpeg-master-latest-win64-gpl\\ffmpeg-master-latest-win64-gpl\\bin";
    private final VEProperty PROPERTY = VEProperty.builder().ffmpegPath(FFMPEG_PATH).build();
    private final FFMpegHelper ffmpegHelper = new FFMpegHelper(PROPERTY);

    /**
     * 测试解析mkv视频文件的章节锚点
     */
    @Test
    public void testParseChapter() throws IOException {
        VideoInfo videoInfo = ffmpegHelper.getVideoInfo(VIDEO_PATH);
        System.out.println(videoInfo);
    }
}
