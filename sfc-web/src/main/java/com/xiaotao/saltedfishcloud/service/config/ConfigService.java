package com.xiaotao.saltedfishcloud.service.config;

import javafx.util.Pair;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public interface ConfigService {
    /**
     * 获取存在的所有配置
     */
    Map<ConfigName, String> getAllConfig();

    /**
     * 从配置表读取一个配置项的值
     * @param key   配置名
     * @return      结果
     */
    String getConfig(ConfigName key);

    /**
     * 设置一个配置项
     * @param key       配置项
     * @param value     配置值
     */
    boolean setConfig(ConfigName key, String value) throws IOException;

    /**
     * 设置一个配置项
     * @param key       配置项
     * @param value     配置值
     */
    boolean setConfig(String key, String value) throws IOException;

    void addConfigChangeListener(Consumer<Pair<ConfigName, String>> listener);
}
