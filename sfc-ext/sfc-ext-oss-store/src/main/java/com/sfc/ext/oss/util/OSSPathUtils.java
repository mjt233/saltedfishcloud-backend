package com.sfc.ext.oss.util;

import com.xiaotao.saltedfishcloud.utils.StringUtils;

public class OSSPathUtils {

    /**
     * 将文件名表示为目录文件名
     * @param name  文件名
     * @return      表示目录的文件名
     */
    public static String toDirectoryName(String name) {
        String path = toOSSObjectName(name);
        if (path.endsWith("/")) {
            return path;
        } else {
            return path + "/";
        }
    }

    /**
     * 将路径转为oss的对象名称（移除前面的/）
     */
    public static String toOSSObjectName(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        } else {
            return path;
        }
    }

    /**
     * 判断给定的路径是否为OSS的目录
     * @param path 路径
     */
    public static boolean isDir(String path) {
        return path != null && path.endsWith("/");
    }

    /**
     * 判断给定的路径是否为根目录
     * @param path 待判断路径
     */
    public static boolean isRootPath(String path) {
        return !StringUtils.hasText(path) || "/".equals(path);
    }

}
