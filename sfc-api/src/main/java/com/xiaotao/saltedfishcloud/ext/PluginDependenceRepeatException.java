package com.xiaotao.saltedfishcloud.ext;

public class PluginDependenceRepeatException extends RuntimeException {
    public PluginDependenceRepeatException(String existName) {
        super(existName);
    }
}
