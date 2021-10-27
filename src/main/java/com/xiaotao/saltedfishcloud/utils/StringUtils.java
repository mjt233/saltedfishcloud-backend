package com.xiaotao.saltedfishcloud.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;

public class StringUtils {
    private final static String PATTERN = "qwertyuiopasdfghjklzxcvbnm";
    private final static int PATTERN_LEN = PATTERN.length();

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
     * 生成一个纯字母随机字符串
     * @param len           生成的字符串长度
     * @return              随机字符串
     */
    public static String getRandomString(int len) {
        return getRandomString(len, true);
    }

    public static String getURLLastName(String url) throws MalformedURLException {
        return getURLLastName(new URL(url));
    }
    /**
     * 获取URL中最后一个目录节点的名称
     * @param url   URL
     * @return      资源名称，若URL中
     */
    public static String getURLLastName(URL url) {
        String path = url.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() -1);
        }
        int i = path.lastIndexOf("/");
        if (i == -1) return null;
        return path.substring(i + 1);
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

}
