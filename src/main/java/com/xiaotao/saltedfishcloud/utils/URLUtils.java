package com.xiaotao.saltedfishcloud.utils;

import javax.servlet.http.HttpServletRequest;

public class URLUtils {
    /**
     * 取URL中的文件路径（仅基于URL原文）
     * @param prefix URL前缀
     * @param request
     * @return
     */
    public static String getRequestFilePath(String prefix, HttpServletRequest request) {
        String requestURI = request.getServletPath();
        return requestURI.substring(prefix.length());
    }
}
