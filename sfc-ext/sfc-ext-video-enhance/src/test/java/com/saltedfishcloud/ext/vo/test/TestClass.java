package com.saltedfishcloud.ext.vo.test;

import com.saltedfishcloud.ext.ve.core.FFMpegInvoker;
import com.saltedfishcloud.ext.ve.core.VideoParser;
import com.saltedfishcloud.ext.ve.model.MediaStream;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestClass {
    private final String VIDEO_PATH = "C:\\Users\\xiaotao\\Downloads\\JoJo's Bizarre Adventure - S05E25 - DUAL 1080p WEB H.264 -NanDesuKa (NF).mkv";
    private final String FFMPEG_PATH = "C:\\DATA\\soft\\ffmpeg-master-latest-win64-gpl\\ffmpeg-master-latest-win64-gpl\\bin";
    private final VEProperty PROPERTY = VEProperty.builder().ffmpegPath(FFMPEG_PATH).build();
    private final FFMpegInvoker ffMpegInvoker = new FFMpegInvoker(PROPERTY);
    private final VideoParser videoParser = new VideoParser(PROPERTY);

    /**
     * 测试解析mkv视频文件的章节锚点
     */
    @Test
    public void testParseChapter() throws IOException {
        VideoInfo videoInfo = this.videoParser.getVideoInfo(VIDEO_PATH);
        System.out.println("==== 章节锚点 ====");
        System.out.println(videoInfo.getChapterList());

        System.out.println("==== 流数据 ====");
        for (MediaStream stream : videoInfo.getMediaStreamList()) {
            System.out.println(stream.toString());
        }
    }
    /**
     * 尝试读取一个字幕类型的数据流并获取其srt格式的字幕文件内容
     */
    @Test
    public void testGetSubtitle() throws IOException {
        VideoInfo videoInfo = videoParser.getVideoInfo(VIDEO_PATH);
        MediaStream stream = videoInfo.getMediaStreamList().stream()
                // 筛选中文字幕
                .filter(e -> e.isSubtitle() && "chi".equals(e.getRemark()) && TypeUtils.toNumber(Long.class, e.getMetadata().get("NUMBER_OF_FRAMES")) > 10)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("找不到字幕流"));
        String srt = ffMpegInvoker.extractSubtitle(VIDEO_PATH, stream.getNo());
        System.out.println(srt);
        System.out.println("=== 来自：" + stream);
    }

    /**
     * 测试流分组
     */
    @Test
    public void testStreamGroup() throws IOException {
        VideoInfo videoInfo = videoParser.getVideoInfo(VIDEO_PATH);
        System.out.println("===== 字幕流 =====");
        videoInfo.getSubtitleStreamList().forEach(System.out::println);
        System.out.println("\n===== 音频流 =====");
        videoInfo.getAudioStreamList().forEach(System.out::println);
        System.out.println("\n===== 视频流 =====");
        videoInfo.getVideoStreamList().forEach(System.out::println);
    }
}
