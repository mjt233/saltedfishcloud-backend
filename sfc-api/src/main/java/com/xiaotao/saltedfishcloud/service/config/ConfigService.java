package com.xiaotao.saltedfishcloud.service.config;


import com.xiaotao.saltedfishcloud.model.Pair;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public interface ConfigService {
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
     * 添加一个当有配置被更改时触发的监听器
     * @param listener  监听器，key为发生改变的配置名，value为新的配置值
     */
    void addConfigSetListener(Consumer<Pair<String, String>> listener);

    /**
     * 添加一个监听指定配置被设置时触发的监听器
     * @param key       被监听的key
     * @param listener  监听器，参数为值
     */
    void addConfigListener(String key, Consumer<String> listener);


    /**
     * 设置存储类型
     * @param type 存储类型
     * @return true表示切换成功，false表示切换被忽略
     * @throws IllegalStateException 数据库配置表无相关信息
     */
    @Transactional(rollbackFor = Exception.class)
    boolean setStoreType(StoreMode type) throws IOException;

}
