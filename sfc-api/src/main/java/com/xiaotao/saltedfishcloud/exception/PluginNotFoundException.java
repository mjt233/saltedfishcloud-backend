package com.xiaotao.saltedfishcloud.exception;

public class PluginNotFoundException extends Exception {
    public PluginNotFoundException(String name) {
        super("找不到插件：" + name);
    }
}
