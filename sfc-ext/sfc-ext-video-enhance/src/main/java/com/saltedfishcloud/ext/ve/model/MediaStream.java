package com.saltedfishcloud.ext.ve.model;

import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 媒体文件流信息
 */
@Data
public class MediaStream {
    private static final Pattern STREAM_REMARK_PATTERN = Pattern.compile("(?<=#\\d\\d?:\\d\\d?(\\[\\d\\d?x\\d\\d?\\])?\\()\\w+(?=\\):)");
    private static final Pattern STREAM_TYPE_PATTERN = Pattern.compile("#.*\\d+(\\[\\w+\\])?(\\(\\w+\\))?: \\w+:");
    private static final Pattern AUDIO_SAMPLE_RATE_PATTERN = Pattern.compile("\\d+(?= Hz)");
    private static final Pattern AUDIO_MODE_PATTERN = Pattern.compile("(?<=Hz, )\\w+(?=,)");
    private static final Pattern STREAM_ENCODE_PATTERN = Pattern.compile("(?<=(Audio|Video): )\\w+");
    private static final Pattern VIDEO_RESOLUTION_PATTERN = Pattern.compile("(?<=, )\\d+x\\d+");
    private static final Pattern VIDEO_FPS_PATTERN = Pattern.compile("\\d+(\\.\\d+)?(?= fps)");
    private static final Pattern STREAM_BPS_PATTERN = Pattern.compile("\\d+(?=\\s+kb/s)");
    /**
     * 流编号
     */
    private String no;

    /**
     * 备注
     */
    private String remark;

    /**
     * 流类型
     */
    private String type;

    /**
     * 元数据
     */
    private Map<String,String> metadata;

    /**
     * 码率（byte per second）
     */
    private Long bps;

    /**
     * 持续时长
     */
    private String duration;

    /**
     * 原始行文本
     */
    private String originLine;

    public void setOriginLine(String originLine) {
        this.originLine = originLine;
        this.parseOriginLine(originLine);
    }

    private void parseOriginLine(String originLine) {
        // 解析备注， #0:43(chi) 取出括号里的chi
        Matcher matcher = STREAM_REMARK_PATTERN.matcher(originLine);
        if (matcher.find()) {
            this.remark = matcher.group();
        }
        matcher = STREAM_TYPE_PATTERN.matcher(originLine);
        if (matcher.find()) {
            // 直接匹配到 #0:3(jpn): Subtitle:
            String t = matcher.group();
            int spaceIdx = t.lastIndexOf(" ");
            this.type = t.substring(spaceIdx + 1, t.length() - 1);
        }
        String bps = StringUtils.matchStr(originLine, STREAM_BPS_PATTERN);
        if (bps != null) {
            this.bps = Long.parseLong(bps);
        }
    }

    public boolean isSubtitle() {
        return "Subtitle".equals(this.type);
    }

    public boolean isVideo() {
        return "Video".equals(this.type);
    }

    public boolean isAudio() {
        return "Audio".equals(this.type);
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
        this.duration = TypeUtils.toString(metadata.get("DURATION"));
        if (this.bps == null) {
            String bpsStr = metadata.get("BPS");
            if (bpsStr != null) {
                this.bps = Long.parseLong(bpsStr);
            }
        }
    }

    protected AudioStream toAudioStream() {
        AudioStream audioStream = new AudioStream();
        BeanUtils.copyProperties(this, audioStream);
        String sampleRate = StringUtils.matchStr(originLine, AUDIO_SAMPLE_RATE_PATTERN);
        if (sampleRate != null) {
            audioStream.setSampleRate(Long.parseLong(sampleRate));
        }
        audioStream.setMode(StringUtils.matchStr(originLine, AUDIO_MODE_PATTERN));
        audioStream.setEncode(StringUtils.matchStr(originLine, STREAM_ENCODE_PATTERN));
        return audioStream;
    }

    protected VideoStream toVideoStream() {
        VideoStream videoStream = new VideoStream();
        BeanUtils.copyProperties(this, videoStream);
        videoStream.setResolution(StringUtils.matchStr(originLine, VIDEO_RESOLUTION_PATTERN));
        String fps = StringUtils.matchStr(originLine, VIDEO_FPS_PATTERN);
        if (fps != null) {
            videoStream.setFrameRage(Double.parseDouble(fps));
        }
        videoStream.setEncode(StringUtils.matchStr(originLine, STREAM_ENCODE_PATTERN));
        return videoStream;
    }

    protected SubtitleStream toSubtitleStream() {
        SubtitleStream subtitleStream = new SubtitleStream();
        BeanUtils.copyProperties(this, subtitleStream);
        subtitleStream.setTitle(getMetadata().get("title"));
        return subtitleStream;
    }

    @Override
    public String toString() {
        return "流编号:" + this.getNo() +
                " 备注:" + this.getRemark() +
                " 类型:" + this.getType() +
                " 码率:" + this.getBps() + "kbps" +
                " 持续时长：" + this.getDuration();
    }
}
