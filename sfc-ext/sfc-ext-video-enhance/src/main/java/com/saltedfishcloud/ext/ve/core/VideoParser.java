package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.model.Chapter;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.model.VideoInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频信息解析器
 */
public class VideoParser {

    private final Pattern STREAM_NO_PATTERN = Pattern.compile("#.*: ");

    private final VEProperty property;
    private final FFMpegInvoker invoker;

    public VideoParser(VEProperty property) {
        this.property = property;
        this.invoker = new FFMpegInvoker(property);
    }

    /**
     * 获取视频信息
     * @param localFilePath 视频本地文件路径
     * @return              视频信息
     */
    public VideoInfo getVideoInfo(String localFilePath) throws IOException {
        VideoInfo videoInfo = new VideoInfo();
        String probeOutput = invoker.executeProbe(localFilePath);
        String[] outputArr = probeOutput.split("\r?\n");
        for (int i = 0; i < outputArr.length; i++) {
            String line = outputArr[i];
            if (!line.startsWith("  ")) {
                continue;
            }

            if (line.equals("  Chapters:")) {
                i += parseChapters(outputArr, i+1, videoInfo::addChapter);
            }
        }
        return videoInfo;
    }

    private Map<String, String> parseMetadata(String[] outputArr, int beginIndex) {
        Map<String, String> metadata = new HashMap<>();
        for (int i = beginIndex; i < outputArr.length; i++) {
            String line = outputArr[i];
            if (!line.startsWith("        ")) {
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

    private int parseChapters(String[] outputArr, int beginIndex, Consumer<Chapter> consumer) {

        for (int i = beginIndex; i < outputArr.length; i++) {
            String line = outputArr[i];
            if (!line.startsWith("    Chapter")) {
                return i;
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
                return i;
            }
            line = outputArr[++i];
            if (!line.equals("      Metadata:")) {
                consumer.accept(chapter);
                continue;
            }
            Map<String, String> metadata = parseMetadata(outputArr, i + 1);
            i += metadata.size();
            chapter.setTitle(metadata.get("title"));
            consumer.accept(chapter);
        }
        return outputArr.length;
    }
}
