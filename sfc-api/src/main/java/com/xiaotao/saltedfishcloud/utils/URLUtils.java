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
}
