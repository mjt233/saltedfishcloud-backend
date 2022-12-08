package com.xiaotao.saltedfishcloud.utils;

import java.util.regex.Pattern;

public class PatternUtils {
    private PatternUtils() {}
    /**
     * 获取匹配前后两个正则表达式之间的内容
     * @param content   内容表达式
     * @param before    内容前的表达式
     * @param after     内容后的表达式
     */
    public static Pattern matchBetween(String content, String before, String after) {
        return Pattern.compile("(?<=" + before +")" + content + "(?=" + after + ")");
    }
}
