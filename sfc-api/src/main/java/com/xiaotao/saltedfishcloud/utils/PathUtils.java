package com.xiaotao.saltedfishcloud.utils;


import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PathUtils {

    private static final Path LOG_PATH;
    private static final Path TEMP_PATH;
    private static final String TEMP_PATH_STR;

    static {
        LOG_PATH = Paths.get("log");
        if (!Files.exists(LOG_PATH)) {
            try {
                Files.createDirectories(LOG_PATH);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        TEMP_PATH = Paths.get(StringUtils.appendPath(System.getProperty("java.io.tmpdir"), "xyy"));
        if (!Files.exists(TEMP_PATH)) {
            try {
                Files.createDirectories(TEMP_PATH);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        TEMP_PATH_STR = TEMP_PATH.toString();
    }

    /**
     * 检查临时目录是否存在，不存在则创建
     */
    private static void checkAndCreateTempPath() {
        if (!Files.exists(TEMP_PATH)) {
            try {
                Files.createDirectories(TEMP_PATH);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 获取系统临时目录
     */
    public static String getTempDirectory() {
        checkAndCreateTempPath();
        return TEMP_PATH_STR;
    }

    public static Path getTempPath() {
        checkAndCreateTempPath();
        return TEMP_PATH;
    }

    /**
     * 在临时目录下随机创建一个临时文件路径
     * @param prefix    临时文件前缀
     * @return  创建的临时文件路径
     */
    public static Path createTemplateFilePath(@Nullable String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return getTempPath().resolve(IdUtil.getUUID() + ".tmp");
        } else {
            return getTempPath().resolve(prefix + IdUtil.getUUID() + ".tmp");
        }
    }

    /**
     * 在临时目录下随机创建一个临时文件路径
     * @return  创建的临时文件路径
     */
    public static Path createTemplateFilePath() {
        return createTemplateFilePath(null);
    }

    /**
     * 获取在临时目录下的二级目录，若二级目录不存在则创建
     * @param path 二级目录
     */
    public static Path getAndCreateTempDirPath(String path) throws IOException {
        Path res = getTempPath().resolve(path);
        if (Files.notExists(res)) {
            Files.createDirectories(res);
        }
        return res;
    }

    /**
     * 获取日志目录
     */
    public static Path getLogDirectory() {
        return LOG_PATH;
    }

    /**
     * 从一个路径字符串中获取他的父级路径
     * @param path  待处理的路径
     * @return      父级路径
     */
    public static String getParentPath(String path) {
        if (path.endsWith("/")) {
            path = path.replaceAll("/+$", "");

            // 类似只有/的路径被正则替换后，无了
            if (path.length() == 0) {
                return "/";
            }
        }
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
     * 移除相同的路径前缀。路径需要满足以"/"开头，且 参数 input 必须与 prefix 表示的路径相同或为子路径，否则会抛出异常<br>
     * 例如：<br>
     * <code>removePrefix("/a/b", "/a/b/c/d")</code> 返回 <code>/c/d</code><br>
     * <code>removePrefix("/a/b", "/a/b")</code> 返回 <code>/</code><br>
     * <code>removePrefix("/a/b", "/a/bc/d")</code> 抛出异常 <br>
     * @param prefix    路径前缀
     * @param input     输入路径
     * @return  移除后的路径
     */
    public static String removePrefix(String prefix, String input) {
        // 1. 检查输入路径和前缀是否以 "/" 开头
        if (prefix == null || input == null || !prefix.startsWith("/") || !input.startsWith("/")) {
            throw new IllegalArgumentException("路径必须以 '/' 开头");
        }

        // 2. 确保 prefix 是 input 的前缀
        if (!input.startsWith(prefix)) {
            throw new IllegalArgumentException("输入路径不是前缀的子路径");
        }

        // 3. 如果 prefix 和 input 相同，返回 "/"
        if (input.equals(prefix)) {
            return "/";
        }

        // 4. 移除前缀部分
        String remainingPath = input.substring(prefix.length());

        // 5. 如果剩余路径为空，返回 "/"
        if (remainingPath.isEmpty()) {
            return "/";
        }

        // 6. 确保返回的路径以 "/" 开头
        if (!remainingPath.startsWith("/")) {
            remainingPath = "/" + remainingPath;
        }

        return remainingPath;
    }

    /**
     * 获取路径中最后一个节点的名称
     * @param path  路径
     * @return      节点名称
     */
    public static String getLastNode(String path) {
        if (path.endsWith("/")) {
            path = path.replaceAll("/+$", "");
        }
        int pos = path.lastIndexOf("/");
        if (pos == -1) {
            return path;
        } else {
            return path.substring(pos + 1);
        }
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
     * 判断路径是否标识了根目录的路径
     */
    public static boolean isRoot(String path) {
        return "/".equals(path);
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
        // 统一格式化，需要“/"开头
        if (a.charAt(0) != '/' && a.charAt(0) != '\\') {
            a = "/" + a;
        }
        if (b.charAt(0) != '/' && b.charAt(0) != '\\') {
            b = "/" + b;
        }

        // 统一格式化，斜杠不能连续重复
        a = a.replaceAll("//+|\\\\+", "/");
        b = b.replaceAll("//+|\\\\+", "/");

        // 统一格式化，末尾需要有“/”
        if (!a.equals("/")) {
            a += '/';
        }
        if (!b.equals("/")) {
            b += '/';
        }
        return b.startsWith(a);
    }
}
