package com.xiaotao.saltedfishcloud.validator;

import java.util.regex.Pattern;

public class FileNameValidator {
    private final static Pattern pattern = Pattern.compile("(^\\.+$)|(\\\\)|(/)", Pattern.MULTILINE);

    /**
     * 验证是否为合法的文件名（不允许出现
     * @param fileName  文件名
     */
    public static void valid(CharSequence ...fileName) {
        for (CharSequence name : fileName) {
            if (pattern.matcher(name).find() || name.length() > 255) {
                throw new IllegalArgumentException("不合法的文件名");
            }
        }
    }
}
