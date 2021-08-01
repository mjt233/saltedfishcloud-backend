package com.xiaotao.saltedfishcloud.service.breakpoint.utils;

import lombok.var;

public class PartParser {
    /**
     * 验证文件块格式是否正确
     * @param part  文件块
     */
    public static boolean validate(String part) {
        if (!part.matches("^(\\d+-)?\\d+$")) {
            return false;
        }

        if(part.matches("^\\d+$")) {
            return true;
        }

        var s = part.split("-");
        try {
            if (Integer.parseInt(s[1]) <= Integer.parseInt(s[0])) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /**
     * 解析文件块描述为文件块数组
     * @param part 文件块
     */
    public static int[] parse(String part) {
        if (!validate(part)) {
            throw new IllegalArgumentException("无效的文件块描述：" + part);
        }
        int[] pair;
        var t = part.split("-", 2);
        pair = new int[2];
        pair[0] = Integer.parseInt(t[0]);
        pair[1] = Integer.parseInt(t[1]);
        var size = pair[1] - pair[0] + 1;
        int[] res = new int[size];
        for (int i = 0; i < res.length; i++) {
            res[i] = pair[0] + i;
        }
        return res;
    }
}
