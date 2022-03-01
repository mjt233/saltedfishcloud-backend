package com.xiaotao.saltedfishcloud.orm.config;

public class ConfigUtils {
    /**
     * 通过setter方法名获取对应的小驼峰命名法的字段名
     * @param name  setter方法名
     * @return      小驼峰命名法的字段名
     */
    public static String getFieldNameByMethodName(String name) {
        if (!name.startsWith("set")) {
            throw new IllegalStateException(name + " 不合法");
        }
        return ((char)(name.charAt(3) + 32) + name.substring(4));
    }
}
