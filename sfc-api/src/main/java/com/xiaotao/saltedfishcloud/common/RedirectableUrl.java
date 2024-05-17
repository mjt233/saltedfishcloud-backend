package com.xiaotao.saltedfishcloud.common;

import org.jetbrains.annotations.NotNull;

/**
 * 可重定向url，用于响应web请求时让客户端转跳到指定的链接。
 */
public interface RedirectableUrl {
    /**
     * 获取重定向目标url
     */
    @NotNull
    String getRedirectUrl();
}
