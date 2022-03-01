package com.xiaotao.saltedfishcloud.orm.config;

import java.util.List;

public interface ConfigureHandler {

    /**
     * 设置一个配置项
     * @param key   配置名
     * @param value 配置值
     * @return      受影响行数
     */
    int setConfig(String key, String value);

    /**
     * 读取一个配置项
     * @param key   配置名
     * @return      配置值
     */
    String getConfig(String key);

    /**
     * 获取所有指定前缀的配置
     * @param prefix    配置节点前缀
     * @return          配置信息
     */
    List<RawConfigEntity> getAllConfigByPrefix(String prefix);
}
