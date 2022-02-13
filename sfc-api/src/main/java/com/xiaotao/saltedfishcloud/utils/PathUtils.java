package com.xiaotao.saltedfishcloud.utils;


import com.xiaotao.saltedfishcloud.helper.PathBuilder;

import java.util.ArrayList;
import java.util.List;

public class PathUtils {
    /**
     * 获取系统临时目录
     */
    public static String getTempDirectory() {
        return System.getProperty("java.io.tmpdir");
    }

    public static String getParentPath(String path) {
        final int i = path.lastIndexOf('/');
        if (i == 0) {
            return "/";
        } else if (i == -1) {
            return "";
        } else {
            return path.substring(0, i);
        }
    }

    /**
     * 获取路径中最后一个节点的名称
     * @TODO 优化性能
     * @param path  路径
     * @return      节点名称
     */
    public static String getLastNode(String path) {
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        return pb.range(1, -1);
    }

    /**
     * 获取该路径途径的所有节点的完整路径<br>
     *      输入：/a/b/c/d
     *      返回：["/a", "/a/b", "/a/b/c", "/a/b/c/d"]
     * @param path  路径
     * @return  所有路径
     */
    public static String[] getAllNode(String path) {
        List<Integer> pos = new ArrayList<>();
        int l = path.length();
        for (int i = 0; i < l; i++) {
            if (path.charAt(i) == '/' && i != 0) {
                pos.add(i);
            }
        }
        int pl = pos.size();
        if (pl == 0) {
            return new String[]{path};
        }
        String[] res = new String[pl + 1];
        for (int i = 0; i < pl; i++) {
            res[i] = path.substring(0, pos.get(i));
        }
        res[res.length - 1] = path;
        return res;
    }


    /**
     * 判断b是否为a的子目录，如<br>
     * <code>isSubDir("/a/b/c", "/a/b/c/d")</code>为true<br>
     * <code>isSubDir("/a/b/c/d", "/a/b/c")</code>为false<br>
     * <code>isSubDir("/a/b/c", "a/b/c/d")</code>为true
     * @param a 目录A
     * @param b 目录B
     * @return 是子目录则为true，否则为false
     */
    public static boolean isSubDir(String a, String b) {
        if (a.charAt(0) != '/' && a.charAt(0) != '\\') {
            a = "/" + a;
        }
        if (b.charAt(0) != '/' && b.charAt(0) != '\\') {
            b = "/" + b;
        }
        a = a.replaceAll("//+|\\\\+", "/");
        b = b.replaceAll("//+|\\\\+", "/");
        return b.startsWith(a);
    }
}
