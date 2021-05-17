package com.xiaotao.saltedfishcloud.utils;

public class StringUtils {
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
