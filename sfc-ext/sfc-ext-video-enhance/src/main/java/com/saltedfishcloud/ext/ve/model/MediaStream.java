package com.saltedfishcloud.ext.ve.model;

import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.Data;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 媒体文件流信息
 */
@Data
public class MediaStream {
    private static final Pattern STREAM_REMARK_PATTERN = Pattern.compile("(?<=#\\d\\d?:\\d\\d?\\()\\w+(?=\\):)");
    private static final Pattern STREAM_TYPE_PATTERN = Pattern.compile("#.*\\d+(\\(\\w+\\))?: \\w+:");
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
        this.bps = TypeUtils.toNumber(Long.class, metadata.get("BPS"));
        this.duration = TypeUtils.toString(metadata.get("DURATION"));
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
