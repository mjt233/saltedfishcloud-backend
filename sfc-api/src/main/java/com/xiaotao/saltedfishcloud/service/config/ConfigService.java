package com.xiaotao.saltedfishcloud.service.config;


import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.model.Pair;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.model.PluginConfigNodeInfo;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ConfigService {

    /**
     * 获取所有插件下的配置信息
     */
    List<PluginConfigNodeInfo> listPluginConfig();

    /**
     * 按自定义SQL操作符和表达式获取配置
     * @param operation     SQL运算符，如：=、like、in
     * @param keyPattern    SQL表达式，如：('a','b')
     */
    Map<String, String> listConfig(String operation, String keyPattern);

    /**
     * 获取存在的所有配置
     */
    Map<String, String> getAllConfig();

    /**
     * 从配置表读取一个配置项的值
     * @param key   配置名
     * @return      结果
     */
    String getConfig(String key);

    /**
     * 设置一个配置项
     * @param key       配置项
     * @param value     配置值
     */
    boolean setConfig(String key, String value) throws IOException;

    /**
     * 批量设置配置项
     */
    boolean batchSetConfig(List<NameValueType<String>> configList) throws IOException;

    /**
     * 添加一个当有配置被更改时触发的监听器
     * @param listener  监听器，key为发生改变的配置名，value为新的配置值
     */
    void addConfigSetListener(Consumer<Pair<String, String>> listener);

    /**
     * 添加一个监听指定配置被设置时触发的监听器
     * @param key       被监听的key
     * @param listener  监听器，参数为值
     */
    void addBeforeSetListener(String key, Consumer<String> listener);

    /**
     * 添加一个监听指定配置被设置后触发的监听器
     * @param key       被监听的key
     * @param listener  监听器，参数为值
     */
    void addAfterSetListener(String key, Consumer<String> listener);


    /**
     * 设置存储类型
     * @param type 存储类型
     * @return true表示切换成功，false表示切换被忽略
     * @throws IllegalStateException 数据库配置表无相关信息
     */
    @Transactional(rollbackFor = Exception.class)
    boolean setStoreType(StoreMode type) throws IOException;

}
