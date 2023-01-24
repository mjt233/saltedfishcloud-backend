package com.xiaotao.saltedfishcloud.ext;

public class PluginInfoException extends RuntimeException {
    public PluginInfoException(String message) {
        super(message);
    }

    public PluginInfoException(String message, Throwable reason) {
        super(message, reason);
    }
}
