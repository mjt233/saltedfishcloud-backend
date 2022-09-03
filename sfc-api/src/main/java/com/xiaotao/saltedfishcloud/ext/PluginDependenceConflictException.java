package com.xiaotao.saltedfishcloud.ext;

public class PluginDependenceConflictException extends RuntimeException {
    public PluginDependenceConflictException(String existName) {
        super(existName);
    }
}
