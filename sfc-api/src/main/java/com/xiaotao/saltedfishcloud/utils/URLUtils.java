package com.xiaotao.saltedfishcloud.utils;

import jakarta.servlet.http.HttpServletRequest;

public class URLUtils {
    /**
     * 获取URL中移除API前缀后请求的文件路径（仅基于URL原文）<br>
     * 示例： 请求的URL：/api/getList/newFolder/photo.png<br>
     * API前缀: /api/getList<br>
     * 返回结果: /newFolder/photo.png<br>
     * @param prefix URL中的API前缀
     * @param request 控制器的request对象
     * @return 获取到的文件路径（以/开头）
     */
    public static String getRequestFilePath(String prefix, HttpServletRequest request) {
        String requestURI = request.getServletPath();
        return requestURI.substring(prefix.length());
    }

    /**
     * 获取URL前面的协议 + 主机名 + 端口部分
     */
    public static String getBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            // 找到协议结束位置
            int protocolEnd = url.indexOf("://");

            int hostEnd;
            if (protocolEnd == -1) {
                // 找到协议后的第一个斜杠位置（跳过 :// 的3个字符）
                hostEnd = url.indexOf("/");
            } else {
                hostEnd = url.indexOf("/", protocolEnd + 3);
            }

            // 如果没有斜杠，说明整个URL就是基础部分
            if (hostEnd == -1) {
                return url;
            }

            // 截取从开始到第一个斜杠之前的部分
            return url.substring(0, hostEnd);
        } catch (Exception e) {
            return url; // 发生异常返回原URL
        }
    }
}
