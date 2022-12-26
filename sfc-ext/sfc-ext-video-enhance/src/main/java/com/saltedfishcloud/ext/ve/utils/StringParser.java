package com.saltedfishcloud.ext.ve.utils;

import lombok.experimental.UtilityClass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class StringParser {
    private final static Pattern TIME_PATTERN = Pattern.compile("(?<=time=)\\d+:\\d+:\\d+");

    /**
     * 从ffmpeg处理进度输出字符串中解析对应的已处理时间，转为秒数
     */
    public static Double parseTimeProgress(String progressOutput) {
        Matcher matcher = TIME_PATTERN.matcher(progressOutput);
        if (matcher.find()) {
            String[] split = matcher.group().split(":");
            return Double.parseDouble(split[0]) * 3600
                    + Double.parseDouble(split[1]) * 60
                    + Double.parseDouble(split[2]);
        } else {
            return null;
        }
    }
}
