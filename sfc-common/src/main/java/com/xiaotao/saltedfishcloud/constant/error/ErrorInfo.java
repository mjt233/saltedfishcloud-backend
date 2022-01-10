package com.xiaotao.saltedfishcloud.constant.error;

import org.springframework.http.HttpStatus;

public interface ErrorInfo {
    /**
     * 获取错误描述信息
     */
    String getMessage();

    /**
     * 获取错误对应的HTTP响应码
     */
    int getStatus();

    /**
     * 获取错误的业务错误标识码
     */
    int getCode();
}
