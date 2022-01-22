package com.xiaotao.saltedfishcloud.service.breakpoint;

import lombok.extern.slf4j.Slf4j;
import lombok.var;

/**
 * 分块字符串解析器
 */
@Slf4j
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
     * 解析文件块描述为文件块数组<br>
     * 例：<br>“11” - 文件块编号[11]<br>
     * "11-13" - 文件块编号[11，12，13]<br>
     * @param part 文件块
     */
    public static int[] parse(String part) {


        // 验证格式是否正确
        if (!validate(part)) {
            throw new IllegalArgumentException("无效的文件块描述：" + part);
        }
        int[] pair;

        // 按-分割
        var t = part.split("-", 2);

        // 为数组分配大小
        pair = new int[t.length];

        // 直接取分割后的第一个。若分割后长度为1，说明不是范围表达式，直接返回该值即可
        pair[0] = Integer.parseInt(t[0]);
        if (t.length == 1) {
            log.debug("解析分块：{} 结果：{}", part, pair);
            return pair;
        }

        // 范围表达式，取第二个值，计算两数之差，生成区间序列
        pair[1] = Integer.parseInt(t[1]);
        var size = pair[1] - pair[0] + 1;
        int[] res = new int[size];
        for (int i = 0; i < res.length; i++) {
            res[i] = pair[0] + i;
        }
        log.debug("解析分块：{} 结果：{}", part, res);
        return res;
    }
}
