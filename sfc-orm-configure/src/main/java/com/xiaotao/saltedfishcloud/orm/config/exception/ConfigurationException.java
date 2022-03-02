package com.xiaotao.saltedfishcloud.orm.config.exception;

/**
 * 配置异常
 */
public class ConfigurationException extends Exception {
    public ConfigurationException() {
    }

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
