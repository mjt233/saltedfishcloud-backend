package com.xiaotao.saltedfishcloud.compress.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CharacterUtils {
    /**
     * 非文字字符表达式
     */
    private final static Pattern NON_TEXT_PATTERN = Pattern.compile("\\s*|\t*|\r*|\n*");


    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return true;
        }
        return false;
    }

    /**
     * 判断是否为乱码
     * @param input 待测试的字符串
     * @return      乱码为true，否则为false
     */
    public static boolean isMessyCode(String input) {
        Matcher m = NON_TEXT_PATTERN.matcher(input);
        String after = m.replaceAll("");
        String temp = after.replaceAll("\\p{P}", "");
        char[] ch = temp.trim().toCharArray();
        float chLength = 0 ;
        float count = 0;
        for (char c : ch) {
            if (!Character.isLetterOrDigit(c)) {
                if (!isChinese(c)) {
                    count = count + 1;
                }
                chLength++;
            }
        }
        float result = count / chLength ;
        return result > 0.4;
    }
}
