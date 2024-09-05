package com.xiaotao.saltedfishcloud.utils;

import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
    /**
     * 随机字符串采用的字符序列
     */
    private final static String PATTERN = "qwertyuiopasdfghjklzxcvbnm";

    /**
     * 随机字符串采用的字符序列长度
     */
    private final static int PATTERN_LEN = PATTERN.length();

    /**
     * 判断a和b是否为相同表示的路径
     */
    public static boolean isPathEqual(String a, String b) {
        boolean eq = Objects.equals(a, b);
        if (eq) {
            return eq;
        }
        if (a == null || b == null) {
            return false;
        }

        if (a.endsWith("/")) {
            a = a.replaceAll("/+$", "");
        }
        if (b.endsWith("/")) {
            b = b.replaceAll("/+$", "");
        }
        a = a.replaceAll("//+", "/");
        b = b.replaceAll("//+", "/");
        return a.equals(b);


    }

    /**
     * 从正则中匹配字符串
     * @param str       待匹配字符串
     * @param pattern   正则
     * @return          返回匹配的字符串，不匹配则null
     */
    public static String matchStr(String str,Pattern pattern) {
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            return matcher.group();
        } else {
            return null;
        }
    }

    /**
     * 以文件路径形式追加字符串，自动处理/的重复问题。<br>
     * 开头不会自动添加/，开头是否有/取决于参数0
     * @param appendData    要追加的各字符串，末尾和首部的/会被忽略，由函数内部自动管理/分割
     * @return  追加后的路径字符串
     */
    public static String appendPath(String...appendData) {
        if (appendData.length == 2 && appendData[0] != null && appendData[0].length() == 0) {
            return appendData[1];
        }

        StringBuilder sb = new StringBuilder();
        String last = null;
        for (String data : appendData) {
            if (data == null || (last != null && "/".equals(data))) continue;

            if (last != null && last.length() != 0) {
                if (!(data.startsWith("/") || last.endsWith("/"))) {
                    sb.append('/');
                } else if (data.startsWith("/") && last.equals("/")) {
                    data = data.replaceAll("^/+", "");
                }
            }
            sb.append(data);
            last = data;
        }
        return sb.toString();
    }

    /**
     * 以文件路径形式追加字符串，自动处理/或\的重复问题。<br>
     * 开头不会自动添加/或\，开头是否有/或\取决于参数0。
     * 使用/还是\取决于操作系统
     * @param appendData    要追加的各字符串，末尾和首部的/或\会被忽略，由函数内部自动管理/或\分割
     * @return  追加后的路径字符串
     */
    public static String appendSystemPath(String...appendData) {
        if (appendData.length == 2 && appendData[0] != null && appendData[0].length() == 0) {
            return appendData[1];
        }
        String regex = "^" + File.separator + "+";

        StringBuilder sb = new StringBuilder();
        String last = null;
        for (String data : appendData) {
            if (data == null || (last != null && File.separator.equals(data))) continue;

            if (last != null && last.length() != 0) {
                if (!(data.startsWith(File.separator) || last.endsWith(File.separator))) {
                    sb.append(File.separator);
                } else if (data.startsWith(File.separator) && last.equals(File.separator)) {
                    data = data.replaceAll(regex, "");
                }
            }
            sb.append(data);
            last = data;
        }
        return sb.toString();
    }

    /**
     * 生成一个纯字母随机字符串
     * @param len           生成的字符串长度
     * @param mixUpperCase  是否混入大写字母
     * @return  随机字符串
     */
    public static String getRandomString(int len, boolean mixUpperCase) {
        StringBuilder sb = new StringBuilder(len);
        Random r = new Random();
        if (mixUpperCase) {
            for (int i = 0; i < len; i++) {
                sb.append((char)(PATTERN.charAt(r.nextInt(PATTERN_LEN)) - (r.nextInt(2) == 0 ? 32 : 0)));
            }
        } else {
            for (int i = 0; i < len; i++) {
                sb.append(PATTERN.charAt(r.nextInt(PATTERN_LEN)));
            }
        }
        return sb.toString();
    }

    /**
     * 判断一个字符串是否与给定的正则表达式匹配
     * @param regex 正则表达式
     * @param input 输入的字符串
     * @return  匹配结果
     */
    public static boolean matchRegex(String regex, CharSequence input) {
        if (input == null) return false;
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(input).find();
    }

    /**
     * 生成一个纯字母随机字符串
     * @param len           生成的字符串长度
     * @return              随机字符串
     */
    public static String getRandomString(int len) {
        return getRandomString(len, true);
    }

    public static String getURLLastName(String url) {
        return getURLLastName(url, "/");
    }

    public static String getURLLastName(String url, String spec) {
        String path = url;
        int qsIndex = path.indexOf("?");
        if (qsIndex != -1) {
            path = path.substring(0, qsIndex);
        }
        if (path.endsWith(spec)) {
            path = path.substring(0, path.length() -1);
        }
        int i = path.lastIndexOf(spec);
        if (i == -1) {
            if (path.length() > 0) {
                return path;
            } else {
                return null;
            }
        }
        return path.substring(i + 1);
    }



    /**
     * 获取URL中最后一个目录节点的名称
     * @param url   URL
     * @return      资源名称，若URL中
     */
    public static String getURLLastName(URL url) {
        return getURLLastName(url.getPath());
    }

    /**
     * 获取md5的最后2级存储路径+文件名<br>
     * 例如输入<code>00a27b5294bbbfa65e9fa57bfdb0fa6a</code><br>
     * 返回00/a2/00a27b5294bbbfa65e9fa57bfdb0fa6a
     * @param md5 md5值
     */
    public static String getUniquePath(String md5) {
        return md5.substring(0,2) + "/" + md5.substring(2,4) + "/" + md5;
    }
    public static String getFormatSize(long size){
        double showSize;
        String suffix = "Byte";
        if(size > 1024 && size <= 1048576){
            suffix = "KiB";
            showSize = size/1024f;
        }else if(size > 1048576 && size <= 1073741824){
            suffix = "MiB";
            showSize = size/1048576f;
        }else if(size > 1073741824){
            suffix = "GiB";
            showSize = size/1073741824f;
        } else {
            showSize = size;
        }
        return String.format("%.2f%s", showSize, suffix);
    }

    /**
     * 获取调用栈字符串文本消息
     * @param throwable 抛出的对象
     * @return          调用栈消息字符串文本
     */
    public static String getInvokeStackMsg(Throwable throwable) {

        try (StringWriter stringWriter = new StringWriter(); PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
            return stringWriter.toString();
        } catch (IOException error) {
            return "获取调用栈信息出错" + getInvokeStackMsg(error);
        }
    }

    /**
     * 生成一个进度条字符串
     * @param loaded    已完成
     * @param total     总量
     * @param len       进度条长度
     * @return          进度条字符串
     */
    public static String getProcStr(long loaded, long total, int len) {
        StringBuilder stringBuilder = new StringBuilder();
        double rate = (double)loaded/total;
        stringBuilder.append(String.format("[%.2f%%][", rate*100));
        int ok = (int) (rate*len);
        for (int i = 0; i < ok; i++) {
            stringBuilder.append("+");
        }
        int n = len - ok;
        for (int i = 0; i < n; i++) {
            stringBuilder.append("-");
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }

    /**
     * 移除字符串前缀
     * @param prefix    前缀
     * @param input     输入字符串
     * @return          移除后的结果
     */
    public static String removePrefix(String prefix, String input) {
        return input.substring(prefix.length());
    }

    /**
     * 解析驼峰命名为短横杠命名
     * @param str   待转换字符串
     */
    public static String camelToKebab(String str) {
        return parseCamelAndJoin(str, '-');
    }

    /**
     * 驼峰命名转为下划线命名
     * @param str   待转换字符串
     */
    public static String camelToUnder(String str) {
        return parseCamelAndJoin(str, '_');
    }

    /**
     * 转为大驼峰
     * @param str   待转换小驼峰字符串
     * @return      转换后的大驼峰字符串
     */
    public static String camelToUpperCamel(String str) {
        if (str.charAt(0) >= 'A' && str.charAt(0) <= 'Z') {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 下划线命名转小驼峰
     * @param str   待转换字符串
     * @return      转换结果
     */
    public static String underToCamel(String str) {
        int length = str.length();
        StringBuilder sb = new StringBuilder(str.length() + 6);
        boolean nextIsUpper = false;
        for (int i = 0; i < length; i++) {
            char c = str.charAt(i);
            if (c == '_') {
                nextIsUpper = true;
                continue;
            }
            if (nextIsUpper) {
                if (c >= 'a' && c <= 'z') {
                    c = Character.toUpperCase(c);
                }
                nextIsUpper = false;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 解析驼峰命名，并将各单词转为小写后用参数连接
     * @param str   待解析字符串
     * @param ch    连接字符
     * @return      处理结果
     */
    public static String parseCamelAndJoin(String str, char ch) {
        int length = str.length();
        StringBuilder sb = new StringBuilder(str.length() + 6);
        for (int i = 0; i < length; i++) {
            char c = str.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                if (sb.length() != 0) {
                    sb.append(ch);
                }
                sb.append((char)(c + 32));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean hasText(@Nullable CharSequence str) {
        return str != null && str.length() > 0 && containsText(str);
    }

    public static boolean hasText(@Nullable String str) {
        return str != null && !str.isEmpty() && containsText(str);
    }

    private static boolean containsText(CharSequence str) {
        int strLen = str.length();

        for(int i = 0; i < strLen; ++i) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }

        return false;
    }
}
