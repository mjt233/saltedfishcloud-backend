package com.sfc.webshell.utils;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ProcessUtils {
    /**
     * 解析一条命令行字符串，拆分为参数列表
     * @param cmd   待解析命令行
     * @return      解析后的参数列表
     */
    public static List<String> parseCommandArgs(String cmd) {
        List<String> res = new ArrayList<>();
        int len = cmd.length();

        // 是否处于字符串中
        boolean inString = false;
        // 是否处于转义中
        boolean inEscape = false;
        // 是否到达一个参数的末尾
        boolean isEnd = false;

        StringBuilder currentArg = new StringBuilder();

        for (int i = 0; i < len; i++) {
            char ch = cmd.charAt(i);
            if (inEscape) {
                if (ch == 'n') {
                    currentArg.append('\n');
                } else if (ch == 't') {
                    currentArg.append('\t');
                } else if (ch == 'r') {
                    currentArg.append('\r');
                } else if (ch == ' ') {
                    currentArg.append(' ');
                } else if (ch == '\\') {
                    currentArg.append('\\');
                } else {
                    currentArg.append('\\').append(ch);
                }
                inEscape = false;
                continue;
            }

            if (ch == '\\') {
                inEscape = true;
                continue;
            }

            if (ch == '"') {
                if (inString) {
                    isEnd = true;
                    inString = false;
                } else {
                    inString = true;
                    continue;
                }
            } else if (ch == ' ' || ch == '\n') {
                if (inString) {
                    currentArg.append(ch);
                    continue;
                } else if (currentArg.length() != 0) {
                    isEnd = true;
                }
            }


            // 到了一个参数判定末尾
            if (isEnd) {
                res.add(currentArg.toString());
                currentArg.setLength(0);
                isEnd = false;
            } else if (ch != ' ') {
                currentArg.append(ch);
            }
        }

        if (currentArg.length() != 0) {
            res.add(currentArg.toString());
        }
        return res;
    }
}
