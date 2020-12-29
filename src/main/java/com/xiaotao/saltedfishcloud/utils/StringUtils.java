package com.xiaotao.saltedfishcloud.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class StringUtils {
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

    public static String linkToURLEncoding(String link) {
        String[] split = link.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < split.length; i++) {
            try {
                String t = URLEncoder.encode(split[i], "utf-8");
                sb.append(t.replaceAll("\\+", "%20"));
                if (i != split.length - 1) {
                    sb.append("/");
                }
            } catch (UnsupportedEncodingException e) { }
        }
        return sb.toString();
    }

    public static String getFileSuffix(String name) {
        String[] split = name.split("\\.");
        if (split.length != 0) {
            return split[split.length - 1].toLowerCase();
        } else {
            return name;
        }
    }

}
