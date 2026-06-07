package com.sfc.pxeboot.server.ipxe;

import java.util.Map;

/**
 * 简单的字符串模板工具。
 * 使用 {{name}} 作为占位符，通过 Map 替换为实际值。
 */
public class ScriptTemplate {

    /**
     * 替换模板中的 {{name}} 占位符。
     *
     * @param template 包含 {{name}} 占位符的模板字符串
     * @param params   占位符名称到实际值的映射
     * @return 替换后的字符串
     */
    public static String render(String template, Map<String, String> params) {
        String result = template;
        for (var entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
