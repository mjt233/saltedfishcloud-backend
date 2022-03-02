package com.xiaotao.saltedfishcloud.orm.config.exception;

/**
 * 找不到配置节点异常
 */
public class ConfigurationKeyNotExistException extends ConfigurationException {

    public ConfigurationKeyNotExistException(String node) {
        super("configuration key not exist: " + node);
    }
}
